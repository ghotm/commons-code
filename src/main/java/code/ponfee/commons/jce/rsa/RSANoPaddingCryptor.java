package code.ponfee.commons.jce.rsa;

import static org.apache.commons.lang3.math.NumberUtils.min;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.Arrays;

import code.ponfee.commons.io.Files;
import code.ponfee.commons.jce.Cryptor;
import code.ponfee.commons.jce.Key;
import code.ponfee.commons.util.SecureRandoms;

/**
 * RSA Cryptor, Without padding
 * RSA私钥解密证明：
 *  等同证明：c^d ≡ m (mod n)
 *      因为：m^e ≡ c (mod n)
 *  于是，c可以写成：c = m^e - kn
 *  将c代入要我们要证明的那个解密规则：(m^e - kn)^d ≡ m (mod n)
 *  等同证明：m^(ed) ≡ m (mod n)
 *  由于：ed ≡ 1 (mod φ(n))
 *  所以：ed = hφ(n)+1
 *  得出：m^(hφ(n)+1) ≡ m (mod n)
 * @author Ponfee
 */
public class RSANoPaddingCryptor extends Cryptor {

    private static final byte ZERO = 0;

    public @Override byte[] encrypt(byte[] input, int length, Key ek) {
        return encrypt(input, length, ek, false);
    }

    public @Override byte[] decrypt(byte[] input, Key dk) {
        return decrypt(input, dk, false);
    }

    public void encrypt(InputStream input, Key ek, OutputStream output) {
        encrypt(input, ek, output, false);
    }

    public void decrypt(InputStream input, Key dk, OutputStream output) {
        decrypt(input, dk, output, false);
    }

    public int getOriginBlockSize(RSAKey rsaKey) {
        // 减一个byte为了防止溢出
        // 此时BigInteger(1, byte[getOriginBlockSize(rsaKey)]) < rsaKey.n
        return rsaKey.n.bitLength() / 8 - 1;
    }

    public int getCipherBlockSize(RSAKey rsaKey) {
        return rsaKey.n.bitLength() / 8;
    }

    public final BigInteger getExponent(RSAKey rsaKey) {
        return rsaKey.isSecret ? rsaKey.d : rsaKey.e;
    }

    /**
     * This method generates a new key for the cryptosystem.
     * @return the new key generated
     */
    public @Override final Key generateKey() {
        return generateKey(2048);
    }

    public final Key generateKey(int keySize) {
        return new RSAKey(keySize);
    }

    public String toString() {
        return this.getClass().getSimpleName();
    }

    protected byte[] encrypt(byte[] input, int length, Key ek, boolean isPadding) {
        RSAKey rsaKey = (RSAKey) ek;
        BigInteger exponent = getExponent(rsaKey);
        //return new BigInteger(1, input).modPow(exponent, rsaKey.n).toByteArray();

        int originBlockSize = this.getOriginBlockSize(rsaKey), // 加密前原文数据块的大小
            cipherBlockSize = this.getCipherBlockSize(rsaKey); // 加密后密文数据块大小

        ByteArrayOutputStream out = new ByteArrayOutputStream(input.length);
        byte[] origin, encrypted;
        try {
            for (int offset = 0, len = input.length; offset < len; offset += originBlockSize) {
                if (isPadding) {
                    // 切割并填充原文数据块
                    origin = encodeBlock(input, offset, min(len, offset + originBlockSize), cipherBlockSize, rsaKey);
                } else {
                    // 切割原文数据块
                    origin = Arrays.copyOfRange(input, offset, min(len, offset + originBlockSize));
                }

                // 加密：encrypted = origin^e mode n
                encrypted = new BigInteger(1, origin).modPow(exponent, rsaKey.n).toByteArray();

                // 固定密文长度
                fixedByteArray(encrypted, cipherBlockSize, out);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new SecurityException(e); // cannot happened
        }
    }

    protected byte[] decrypt(byte[] input, Key dk, boolean isPadding) {
        RSAKey rsaKey = (RSAKey) dk;
        BigInteger exponent = getExponent(rsaKey);
        //return new BigInteger(1, input).modPow(exponent, rsaKey.n).toByteArray();

        int cipherBlockSize = this.getCipherBlockSize(rsaKey), // 加密后密文数据块的大小
            originBlockSize = this.getOriginBlockSize(rsaKey);
        ByteArrayOutputStream output = new ByteArrayOutputStream(input.length);
        byte[] encrypted, origin;
        try {
            for (int offset = 0, len = input.length; offset < len; offset += cipherBlockSize) {
                // 切割密文数据块
                encrypted = Arrays.copyOfRange(input, offset, min(len, offset + cipherBlockSize));

                // 解密：origin = encrypted^d mode n
                origin = new BigInteger(1, encrypted).modPow(exponent, rsaKey.n).toByteArray();

                if (isPadding) {
                    // 解码数据块
                    decodeBlock(origin, cipherBlockSize, output);
                } else {
                    if (offset + cipherBlockSize < len) {
                        // 固定明文长度
                        fixedByteArray(origin, originBlockSize, output);
                    } else {
                        // 去掉原文前缀0
                        trimByteArray(origin, output);
                    }
                }
            }
            return output.toByteArray();
        } catch (IOException e) {
            throw new SecurityException(e); // cannot happened
        }
    }

    protected void encrypt(InputStream input, Key ek, OutputStream output, boolean isPadding) {
        RSAKey rsaKey = (RSAKey) ek;
        BigInteger exponent = this.getExponent(rsaKey);
        int cipherBlockSize = this.getCipherBlockSize(rsaKey); // 加密后密文数据块大小

        byte[] buffer = new byte[getOriginBlockSize(rsaKey)], origin, encrypted;
        try {
            for (int len; (len = input.read(buffer)) != Files.EOF;) {
                if (isPadding) {
                    // 切割并填充原文数据块
                    origin = encodeBlock(buffer, 0, len, cipherBlockSize, rsaKey);
                } else {
                    // 切割原文数据块
                    origin = Arrays.copyOfRange(buffer, 0, len);
                }

                // 加密：encrypted = origin^e mode n
                encrypted = new BigInteger(1, origin).modPow(exponent, rsaKey.n).toByteArray();

                // 固定密文长度
                fixedByteArray(encrypted, cipherBlockSize, output);
            }
            output.flush();
        } catch (IOException e) {
            throw new SecurityException(e);
        }
    }

    protected void decrypt(InputStream input, Key dk, OutputStream output, boolean isPadding) {
        RSAKey rsaKey = (RSAKey) dk;
        BigInteger exponent = getExponent(rsaKey);

        int cipherBlockSize = this.getCipherBlockSize(rsaKey), // 加密后密文数据块的大小
            originBlockSize = this.getOriginBlockSize(rsaKey);
        byte[] buffer = new byte[cipherBlockSize], encrypted, origin;
        try {
            int inputLen = input.available();
            for (int len, offset = 0; (len = input.read(buffer)) != Files.EOF; offset += cipherBlockSize) {
                // 切割密文数据块
                encrypted = Arrays.copyOfRange(buffer, 0, len);

                // 解密：origin = encrypted^d mode n
                origin = new BigInteger(1, encrypted).modPow(exponent, rsaKey.n).toByteArray();

                if (isPadding) {
                    // 解码数据块
                    decodeBlock(origin, cipherBlockSize, output);
                } else {
                    if (offset + cipherBlockSize < inputLen) {
                        // 固定明文长度
                        fixedByteArray(origin, originBlockSize, output);
                    } else {
                        // 去掉原文前缀0
                        trimByteArray(origin, output);
                    }
                }
            }
            output.flush();
        } catch (IOException e) {
            throw new SecurityException(e);
        }
    }

    // --------------------------------private methods-------------------------------
    private static void fixedByteArray(byte[] data, int fixedSize, OutputStream out)
        throws IOException {
        if (data.length < fixedSize) {
            // 加前缀0补全到固定字节数：encryptedBlockSize
            for (int i = 0, heading = fixedSize - data.length; i < heading; i++) {
                out.write(ZERO);
            }
            out.write(data, 0, data.length);
        } else {
            // 舍去前面的0
            out.write(data, data.length - fixedSize, fixedSize);
        }
    }

    private static void trimByteArray(byte[] data, OutputStream out)
        throws IOException {
        int i = 0, len = data.length;
        for (; i < len; i++) {
            if (data[i] != ZERO) {
                break;
            }
        }
        if (i < len) {
            out.write(data, i, len - i);
        }
    }

    /**
     * 原文进行编码填充
     * 
     * EB = 00 || BT || PS || 00 || D
     * BT：公钥为0x02；私钥为0x00或0x01
     * PS：BT为0则PS全部为0x00；BT为0x01则全部为0xFF；BT为0x02则为随机数，但不能为0
     * 
     * 对于BT为00的，数据D就不能以00字节开头，因为这时候你PS填充的也是00，
     * 会分不清哪些是填充数据哪些是明文数据<p>
     * 
     * 如果你使用私钥加密，建议你BT使用01，保证了安全性
     * 对于BT为02和01的，PS至少要有8个字节长
     * 
     * @param input  数据
     * @param from   开始位置
     * @param to     结束位置
     * @param cipherBlockSize 模字节长（modules/8）
     * @param rsaKey 密钥
     * @return
     */
    private static byte[] encodeBlock(byte[] input, int from, int to, 
                                      int cipherBlockSize, RSAKey rsaKey) {
        int length = to - from;
        if (length > cipherBlockSize) {
            throw new IllegalArgumentException("input data too large");
        } else if (cipherBlockSize - length - 3 < 8) {
            throw new IllegalArgumentException("the padding too small");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream(cipherBlockSize);
        baos.write(0x00); // 00

        if (rsaKey.isSecret) {
            // 私钥填充
            baos.write(0x01); // BT
            for (int i = 2, pLen = cipherBlockSize - length - 1; i < pLen; i++) {
                baos.write(0xFF);
            }
        } else {
            // 公钥填充，规定此处至少要8个字节
            baos.write(0x02); // BT
            byte b;
            for (int i = 2, pLen = cipherBlockSize - length - 1; i < pLen; i++) {
                do {
                    b = (byte) SecureRandoms.nextInt();
                } while (b == ZERO);
                baos.write(b);
            }
        }

        baos.write(0x00); // 00

        baos.write(input, from, length); // D
        return baos.toByteArray();
    }

    /**
     * 解码原文填充（前缀0被舍去，只有127位）
     * @param input
     * @param cipherBlockSize
     * @param out
     * @throws IOException
     */
    private static void decodeBlock(byte[] input, int cipherBlockSize, OutputStream out)
        throws IOException {
        int removedZeroLen;
        if (input[0] == ZERO) {
            removedZeroLen = 0;
        } else {
            // 前缀0已在BigInteger转byte[]时被舍去
            removedZeroLen = 1;
        }

        // 输入数据长度必须等于数据块长
        if (input.length != cipherBlockSize - removedZeroLen) {
            throw new IllegalArgumentException("block incorrect size");
        }

        // check BT
        byte type = input[1 - removedZeroLen];
        if (type != 1 && type != 2) {
            throw new IllegalArgumentException("unknown block type");
        }

        // PS
        int start = 2 - removedZeroLen;
        for (; start != input.length; start++) {
            byte pad = input[start];
            if (pad == 0) {
                break;
            }
            if (type == 1 && pad != (byte) 0xff) {
                throw new IllegalArgumentException("block padding incorrect");
            }
        }

        // get D
        start++; // data should start at the next byte
        if (start > input.length || start < 11 - removedZeroLen) {
            throw new IllegalArgumentException("no data in block");
        }
        out.write(input, start, input.length - start);
    }

}
