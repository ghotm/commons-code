package code.ponfee.commons.jce.security;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import javax.crypto.Cipher;

import code.ponfee.commons.jce.RSASignAlgorithm;

/**
 * RSA Cryptor
 * 加/解密
 * 签名/验签
 * @author fupf
 */
public final class RSACryptor {
    private RSACryptor() {}

    static final String ALG_RSA = "RSA";

    public static RSAKeyPair genRsaKeyPair() {
        return genRSAKeyPair(1024);
    }

    /**
     * 密钥生成
     * @param keySize 密钥长度（位）
     * @return
     */
    public static RSAKeyPair genRSAKeyPair(int keySize) {
        KeyPairGenerator keyPairGen;
        try {
            keyPairGen = KeyPairGenerator.getInstance(ALG_RSA);
        } catch (Exception e) {
            throw new SecurityException(e);
        }
        keyPairGen.initialize(keySize);
        KeyPair pair = keyPairGen.generateKeyPair();
        return new RSAKeyPair((RSAPrivateKey) pair.getPrivate(), (RSAPublicKey) pair.getPublic());
    }

    // ---------------------------------------sign/verify---------------------------------------
    /**
     * SHA1签名
     * @param data
     * @param privateKey
     * @return
     */
    public static byte[] signSha1(byte[] data, RSAPrivateKey privateKey) {
        return sign(data, privateKey, RSASignAlgorithm.SHA1withRSA);
    }

    /**
     * MD5签名
     * @param data
     * @param privateKey
     * @return
     */
    public static byte[] signMd5(byte[] data, RSAPrivateKey privateKey) {
        return sign(data, privateKey, RSASignAlgorithm.MD5withRSA);
    }

    /**
     * SHA1
     * @param data
     * @param publicKey
     * @param signed
     * @return
     */
    public static boolean verifySha1(byte[] data, RSAPublicKey publicKey, byte[] signed) {
        return verify(data, publicKey, signed, RSASignAlgorithm.SHA1withRSA);
    }

    /**
     * MD5验签
     * @param data
     * @param publicKey
     * @param signed
     * @return
     */
    public static boolean verifyMd5(byte[] data, RSAPublicKey publicKey, byte[] signed) {
        return verify(data, publicKey, signed, RSASignAlgorithm.MD5withRSA);
    }

    /**
     * <pre>
     *   1、可以通过修改生成密钥的长度来调整密文长度
     *   2、不管明文长度是多少，RSA生成的密文长度总是固定的
     *   3、明文长度不能超过密钥长度：
     *     1）sun jdk默认的RSA加密实现不允许明文长度超过密钥长度减去11字节（byte）：比如1024位（bit）的密钥，则待加密的明文最长为1024/8-11=117（byte）
     *     2）BouncyCastle提供的加密算法能够支持到的RSA明文长度最长为密钥长度
     *   4、每次生成的密文都不一致证明加密算法安全：这是因为在加密前使用RSA/None/PKCS1Padding对明文信息进行了随机数填充
     *   5、javax.crypto.Cipher是线程不安全的
     * </pre>
     * 
     * 大数据分块加密
     * @param data 源数据
     * @param Key
     * @return
     */
    public static <T extends Key & RSAKey> byte[] encrypt(byte[] data, T key) {
        try {
            Cipher cipher = Cipher.getInstance(key.getAlgorithm());
            cipher.init(Cipher.ENCRYPT_MODE, key);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int inputLen = data.length, modLen = key.getModulus().bitLength() / 8 - 11;
            byte[] block;
            for (int len, i = 0, offSet = 0; inputLen - offSet > 0; i++, offSet = i * modLen) {
                // 对数据分段加密
                len = (inputLen - offSet > modLen) ? modLen : inputLen - offSet;
                block = cipher.doFinal(data, offSet, len);
                out.write(block, 0, block.length);
            }
            out.flush();
            return out.toByteArray();
        } catch (Exception e) {
            throw new SecurityException(e);
        }
    }

    /**
     * 大数据分块解密
     * @param encrypted
     * @param key
     * @return
     */
    public static <T extends Key & RSAKey> byte[] decrypt(byte[] encrypted, T key) {
        try {
            Cipher cipher = Cipher.getInstance(key.getAlgorithm());
            cipher.init(Cipher.DECRYPT_MODE, key);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int inputLen = encrypted.length, modLen = key.getModulus().bitLength() / 8;
            byte[] block;
            for (int len, i = 0, offSet = 0; inputLen - offSet > 0; i++, offSet = i * modLen) {
                // 对数据分段解密
                len = (inputLen - offSet > modLen) ? modLen : inputLen - offSet;
                block = cipher.doFinal(encrypted, offSet, len);
                out.write(block, 0, block.length);
            }
            out.flush();
            return out.toByteArray();
        } catch (Exception e) {
            throw new SecurityException(e);
        }
    }

    // -------------------------------private methods-------------------------------
    /**
     * 数据签名
     * @param data
     * @param privateKey
     * @param algId
     * @return
     */
    private static byte[] sign(byte[] data, RSAPrivateKey privateKey, RSASignAlgorithm alg) {
        try {
            Signature signature = Signature.getInstance(alg.name());
            signature.initSign(privateKey);
            signature.update(data);
            return signature.sign();
        } catch (Exception e) {
            throw new SecurityException(e);
        }
    }

    /**
     * 验签
     * @param data
     * @param publicKey
     * @param signed
     * @param algId
     * @return
     */
    private static boolean verify(byte[] data, RSAPublicKey publicKey, byte[] signed, RSASignAlgorithm alg) {
        try {
            Signature signature = Signature.getInstance(alg.name());
            signature.initVerify(publicKey);
            signature.update(data);
            return signature.verify(signed);
        } catch (Exception e) {
            throw new SecurityException(e);
        }
    }

    /**
     * RSA密钥对
     */
    public static final class RSAKeyPair implements Serializable {
        private static final long serialVersionUID = -1592700389671199076L;
        private final RSAPrivateKey privateKey;
        private final RSAPublicKey publicKey;

        private RSAKeyPair(RSAPrivateKey privateKey, RSAPublicKey publicKey) {
            this.privateKey = privateKey;
            this.publicKey = publicKey;
        }

        public RSAPrivateKey getPrivateKey() {
            return privateKey;
        }

        public RSAPublicKey getPublicKey() {
            return publicKey;
        }

        public String getPkcs8PrivateKey() {
            return RSAPrivateKeys.toPkcs8(privateKey);
        }

        public String getPkcs1PrivateKey() {
            return RSAPrivateKeys.toPkcs1(privateKey);
        }

        public String getPkcs8PublicKey() {
            return RSAPublicKeys.toPkcs8(publicKey);
        }

        public String getPkcs1PublicKey() {
            return RSAPublicKeys.toPkcs1(publicKey);
        }
    }

}
