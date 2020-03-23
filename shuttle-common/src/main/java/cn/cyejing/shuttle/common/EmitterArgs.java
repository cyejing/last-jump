package cn.cyejing.shuttle.common;

import io.netty.util.internal.StringUtil;
import lombok.Data;

/**
 * @author Born
 */
@Data
public class EmitterArgs extends BootArgs {

    protected int port = 14843;

    @Override
    protected boolean verify() {
        super.verify();
        if (StringUtil.isNullOrEmpty(this.remoteHost)) {
            throw new IllegalArgumentException("remoteHost must config, for args \"--remoteHost=x.x.x.x\"");
        }

        return true;
    }
}
