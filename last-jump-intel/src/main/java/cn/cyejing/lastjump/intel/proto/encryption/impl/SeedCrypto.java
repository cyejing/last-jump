package cn.cyejing.lastjump.intel.proto.encryption.impl;

import cn.cyejing.lastjump.intel.proto.encryption.CryptoBase;
import java.io.ByteArrayOutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.crypto.StreamBlockCipher;
import org.bouncycastle.crypto.engines.SEEDEngine;
import org.bouncycastle.crypto.modes.CFBBlockCipher;

public class SeedCrypto extends CryptoBase {

    public final static String CIPHER_SEED_CFB = "seed-cfb";

    public static Map<String, String> getCiphers() {
        Map<String, String> ciphers = new HashMap<>();
        ciphers.put(CIPHER_SEED_CFB, SeedCrypto.class.getName());

        return ciphers;
    }

    public SeedCrypto(String name, String password) {
        super(name, password);
    }

    @Override
    public int getKeyLength() {
        return 16;
    }

    @Override
    protected StreamBlockCipher getCipher(boolean isEncrypted) throws InvalidAlgorithmParameterException {
        SEEDEngine engine = new SEEDEngine();
        StreamBlockCipher cipher;

        if (_name.equals(CIPHER_SEED_CFB)) {
            cipher = new CFBBlockCipher(engine, getIVLength() * 8);
        }
        else {
            throw new InvalidAlgorithmParameterException(_name);
        }

        return cipher;
    }

    @Override
    public int getIVLength() {
        return 16;
    }

    @Override
    protected SecretKey getKey() {
        return new SecretKeySpec(_ssKey.getEncoded(), "AES");
    }

    @Override
    protected void _encrypt(byte[] data, ByteArrayOutputStream stream) {
        int noBytesProcessed;
        byte[] buffer = new byte[data.length];

        noBytesProcessed = encCipher.processBytes(data, 0, data.length, buffer, 0);
        stream.write(buffer, 0, noBytesProcessed);
    }

    @Override
    protected void _decrypt(byte[] data, ByteArrayOutputStream stream) {
        int noBytesProcessed;
        byte[] buffer = new byte[data.length];

        noBytesProcessed = decCipher.processBytes(data, 0, data.length, buffer, 0);
        stream.write(buffer, 0, noBytesProcessed);
    }
}