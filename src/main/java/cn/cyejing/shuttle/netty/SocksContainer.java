/*
 * frxs Inc.  湖南兴盛优选电子商务有限公司.
 * Copyright (c) 2017-2019. All Rights Reserved.
 */
package cn.cyejing.shuttle.netty;

import cn.cyejing.shuttle.common.Config;
import cn.cyejing.shuttle.utils.SocksServerUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerExpectContinueHandler;
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler;
import io.netty.handler.codec.socksx.v4.Socks4CommandRequest;
import io.netty.handler.codec.socksx.v4.Socks4CommandType;
import io.netty.handler.codec.socksx.v5.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLException;
import java.io.InputStream;

/**
 * <B>主类名称：</B><BR>
 * <B>概要说明：</B><BR>
 *
 * @author Born
 * @since 2020年06月25日 1:20 下午
 */
@Slf4j
public class SocksContainer {

    private final Config config;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workGroup;
    private final ServerBootstrap serverBootstrap;
    private SslContext sslContext;

    public SocksContainer(Config config) throws SSLException {
        this.config = config;
        this.bossGroup = new NioEventLoopGroup(1);
        this.workGroup = new NioEventLoopGroup();

        InputStream certificateFile = getClass().getClassLoader().getResourceAsStream(config.getCertificateFile());
        InputStream keyFile = getClass().getClassLoader().getResourceAsStream(config.getKeyFile());
        sslContext = SslContextBuilder.forServer(certificateFile, keyFile)
                .startTls(true)
                .clientAuth(ClientAuth.OPTIONAL)
                .build();

        this.serverBootstrap = new ServerBootstrap()
                .group(bossGroup, workGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel sc) throws Exception {
                        sc.pipeline().addLast(
                                new LoggingHandler(LogLevel.INFO),
                                new SocksPortUnificationServerHandler(),
                                new SocksServerHandler()
                        );
                    }
                });

    }

    public void bind(int port) throws InterruptedException {
        serverBootstrap.bind(port).sync();
    }

    public static class SocksServerHandler extends SimpleChannelInboundHandler<SocksMessage> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, SocksMessage socksRequest) throws Exception {
            switch (socksRequest.version()) {
                case SOCKS5:
                    if (socksRequest instanceof Socks5InitialRequest) {
                        // auth support example
                        //ctx.pipeline().addFirst(new Socks5PasswordAuthRequestDecoder());
                        //ctx.write(new DefaultSocks5AuthMethodResponse(Socks5AuthMethod.PASSWORD));
                        ctx.pipeline().addFirst(new Socks5CommandRequestDecoder());
                        ctx.writeAndFlush(new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH));
                    } else if (socksRequest instanceof Socks5PasswordAuthRequest) {
                        ctx.pipeline().addFirst(new Socks5CommandRequestDecoder());
                        ctx.writeAndFlush(new DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS));
                    } else if (socksRequest instanceof Socks5CommandRequest) {
                        Socks5CommandRequest socks5CmdRequest = (Socks5CommandRequest) socksRequest;
                        if (socks5CmdRequest.type() == Socks5CommandType.CONNECT) {
                            ctx.pipeline().addLast(new SocksServerConnectHandler());
                            ctx.pipeline().remove(this);
                            ctx.fireChannelRead(socksRequest);
                        } else {
                            ctx.close();
                        }
                    } else {
                        ctx.close();
                    }
                    break;
                case UNKNOWN:
                    ctx.close();
                    break;
            }
        }
    }

    public static class SocksServerConnectHandler extends SimpleChannelInboundHandler<SocksMessage> {
        private final Bootstrap b = new Bootstrap();

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, SocksMessage message) throws Exception {
            if (message instanceof Socks5CommandRequest) {
                final Socks5CommandRequest request = (Socks5CommandRequest) message;
                final Channel inboundChannel = ctx.channel();
                b.group(inboundChannel.eventLoop())
                        .channel(NioSocketChannel.class)
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                        .option(ChannelOption.SO_KEEPALIVE, true)
                        .handler(new LoggingHandler(LogLevel.INFO));

                b.connect(request.dstAddr(), request.dstPort()).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (future.isSuccess()) {
                            final Channel outboundChannel = future.channel();

                            ChannelFuture responseFuture =
                                    inboundChannel.writeAndFlush(new DefaultSocks5CommandResponse(
                                            Socks5CommandStatus.SUCCESS,
                                            request.dstAddrType(),
                                            request.dstAddr(),
                                            request.dstPort()));

                            responseFuture.addListener(new ChannelFutureListener() {
                                @Override
                                public void operationComplete(ChannelFuture channelFuture) {
                                    ctx.pipeline().remove(SocksServerConnectHandler.this);
                                    System.out.println("connect");
                                    outboundChannel.pipeline().addLast(new RelayHandler(inboundChannel));
                                    ctx.pipeline().addLast(new RelayHandler(outboundChannel));
                                }
                            });

                            // Connection established use handler provided results
                        } else {
                            // Close the connection if the connection attempt has failed.
                            ctx.channel().writeAndFlush(
                                    new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, request.dstAddrType()));

                            SocksServerUtils.closeOnFlush(ctx.channel());
                        }
                    }
                });
            } else {
                ctx.close();
            }
        }
    }
}