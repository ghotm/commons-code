package code.ponfee.commons.jce.hash;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Hex;

import code.ponfee.commons.io.Files;
import code.ponfee.commons.jce.HmacAlgorithms;
import code.ponfee.commons.jce.Providers;
import code.ponfee.commons.util.MavenProjects;
import code.ponfee.commons.util.SecureRandoms;

/**
 * HMAC的一个典型应用是用在“质询/响应”（Challenge/Response）身份认证中
 * Hmac算法封装，计算“text”的HMAC：
 * <code>
 *   if (length(K) > blocksize) {
 *       K = H(K) // keys longer than blocksize are shortened
 *   } else if (length(key) < blocksize) {
 *       K += [0x00 * (blocksize - length(K))] // keys shorter than blocksize are zero-padded 
 *   }
 *   opad = [0x5c * B] XOR K
 *   ipad = [0x36 * B] XOR K
 *   hash = H(opad + H(ipad + text))
 * </code>
 * 其中：H为散列函数，K为密钥，text为数据，
 *     B表示数据块的字长（the blocksize is that of the underlying hash function）
 * @author fupf
 */
public final class HmacUtils {

    private static final int BUFF_SIZE = 4096;

    public static byte[] sha1(byte[] key, byte[] data) {
        return crypt(key, data, HmacAlgorithms.HmacSHA1);
    }

    public static byte[] sha1(byte[] key, InputStream data) {
        return crypt(key, data, HmacAlgorithms.HmacSHA1);
    }

    public static String sha1Hex(byte[] key, byte[] data) {
        return Hex.encodeHexString(sha1(key, data));
    }

    public static String sha1Hex(byte[] key, InputStream data) {
        return Hex.encodeHexString(sha1(key, data));
    }

    public static byte[] md5(byte[] key, byte[] data) {
        return crypt(key, data, HmacAlgorithms.HmacMD5);
    }

    public static String md5Hex(byte[] key, byte[] data) {
        return Hex.encodeHexString(md5(key, data));
    }

    public static byte[] sha224(byte[] key, byte[] data) {
        return crypt(key, data, HmacAlgorithms.HmacSHA224);
    }

    public static String sha224Hex(byte[] key, byte[] data) {
        return Hex.encodeHexString(sha224(key, data));
    }

    public static byte[] sha256(byte[] key, byte[] data) {
        return crypt(key, data, HmacAlgorithms.HmacSHA256);
    }

    public static String sha256Hex(byte[] key, byte[] data) {
        return Hex.encodeHexString(sha256(key, data));
    }

    public static byte[] sha384(byte[] key, byte[] data) {
        return crypt(key, data, HmacAlgorithms.HmacSHA384);
    }

    public static String sha384Hex(byte[] key, byte[] data) {
        return Hex.encodeHexString(sha384(key, data));
    }

    public static byte[] sha512(byte[] key, byte[] data) {
        return crypt(key, data, HmacAlgorithms.HmacSHA512);
    }

    public static String sha512Hex(byte[] key, byte[] data) {
        return Hex.encodeHexString(sha512(key, data));
    }

    public static byte[] ripeMD128(byte[] key, byte[] data) {
        return crypt(key, data, HmacAlgorithms.HmacRipeMD128, Providers.BC);
    }

    public static String ripeMD128Hex(byte[] key, byte[] data) {
        return Hex.encodeHexString(ripeMD128(key, data));
    }

    public static byte[] ripeMD160(byte[] key, byte[] data) {
        return crypt(key, data, HmacAlgorithms.HmacRipeMD160, Providers.BC);
    }

    public static String ripeMD160Hex(byte[] key, byte[] data) {
        return Hex.encodeHexString(ripeMD160(key, data));
    }

    public static byte[] ripeMD256(byte[] key, byte[] data) {
        return crypt(key, data, HmacAlgorithms.HmacRipeMD256, Providers.BC);
    }

    public static String ripeMD256Hex(byte[] key, byte[] data) {
        return Hex.encodeHexString(ripeMD256(key, data));
    }

    public static byte[] ripeMD320(byte[] key, byte[] data) {
        return crypt(key, data, HmacAlgorithms.HmacRipeMD320, Providers.BC);
    }

    public static String ripeMD320Hex(byte[] key, byte[] data) {
        return Hex.encodeHexString(ripeMD320(key, data));
    }

    public static Mac getInitializedMac(HmacAlgorithms algorithm, byte[] key) {
        return getInitializedMac(algorithm, null, key);
    }

    public static Mac getInitializedMac(HmacAlgorithms algorithm, 
                                        Provider provider, byte[] key) {
        if (key == null) {
            throw new IllegalArgumentException("Null key");
        }

        try {
            Mac mac = (provider == null)
                ? Mac.getInstance(algorithm.name()) 
                : Mac.getInstance(algorithm.name(), provider);
            mac.init(new SecretKeySpec(key, mac.getAlgorithm()));
            return mac;
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("unknown algorithm: " + algorithm);
        } catch (final InvalidKeyException e) {
            throw new IllegalArgumentException("invalid key: " + Hex.encodeHexString(key));
        }
    }

    public static byte[] crypt(byte[] key, byte[] data, HmacAlgorithms alg) {
        return crypt(key, data, alg, null);
    }

    public static byte[] crypt(byte[] key, byte[] data, 
                               HmacAlgorithms alg, Provider provider) {
        return getInitializedMac(alg, provider, key).doFinal(data);
    }

    public static byte[] crypt(byte[] key, InputStream input, 
                               HmacAlgorithms alg) {
        return crypt(key, input, alg, null);
    }

    public static byte[] crypt(byte[] key, InputStream input,
                               HmacAlgorithms alg, Provider provider) {
        try (InputStream in = input) {
            Mac mac = getInitializedMac(alg, provider, key);
            byte[] buffer = new byte[BUFF_SIZE];
            for (int len; (len = in.read(buffer)) != Files.EOF;) {
                mac.update(buffer, 0, len);
            }
            return mac.doFinal();
        } catch (IOException e) {
            throw new IllegalArgumentException("read data error:" + e);
        }
    }

    public static void main(String[] args) throws FileNotFoundException {
        byte[] key = SecureRandoms.nextBytes(16);
        System.out.println(sha1Hex(key, new FileInputStream(MavenProjects.getMainJavaFile(HmacUtils.class))));
        System.out.println(sha1Hex(key, new FileInputStream(MavenProjects.getMainJavaFile(HmacUtils.class))));
        System.out.println(ripeMD128Hex(key, "abc".getBytes()));
        System.out.println(ripeMD160Hex(key, "abc".getBytes()));
        System.out.println(ripeMD256Hex(key, "abc".getBytes()));
        System.out.println(ripeMD320Hex(key, "abc".getBytes()));
    }
}
