package code.ponfee.commons.jce.sm;

import org.apache.commons.codec.binary.Hex;

/**
 * SM3摘要算法
 * @author Ponfee
 */
public class SM3Digest {

    /** SM3值的长度 */
    private static final int BYTE_LENGTH = 32;

    /** SM3分组长度 */
    private static final int BLOCK_LENGTH = 64;

    /** 缓冲区长度 */
    private static final int BUFFER_LENGTH = BLOCK_LENGTH * 1;

    /** 缓冲区 */
    private byte[] xBuf = new byte[BUFFER_LENGTH];

    /** 缓冲区偏移量 */
    private int xBufOff;

    /** 初始向量 */
    private byte[] V = SM3.IV.clone();

    private int cntBlock = 0;

    public SM3Digest() {}

    public SM3Digest(SM3Digest t) {
        System.arraycopy(t.xBuf, 0, this.xBuf, 0, t.xBuf.length);
        this.xBufOff = t.xBufOff;
        System.arraycopy(t.V, 0, this.V, 0, t.V.length);
    }

    /**
     * SM3结果输出
     * @param out 保存SM3结构的缓冲区
     * @param outOff 缓冲区偏移量
     * @return
     */
    public int doFinal(byte[] out, int outOff) {
        byte[] tmp = doFinal();
        System.arraycopy(tmp, 0, out, 0, tmp.length);
        return BYTE_LENGTH;
    }

    public void reset() {
        xBufOff = 0;
        cntBlock = 0;
        V = SM3.IV.clone();
    }

    public void update(byte[] in) {
        this.update(in, 0, in.length);
    }

    /**
     * 明文输入
     * @param in 明文输入缓冲区
     * @param inOff 缓冲区偏移量
     * @param len 明文长度
     */
    public void update(byte[] in, int inOff, int len) {
        int partLen = BUFFER_LENGTH - xBufOff;
        int inputLen = len;
        int dPos = inOff;
        if (partLen < inputLen) {
            System.arraycopy(in, dPos, xBuf, xBufOff, partLen);
            inputLen -= partLen;
            dPos += partLen;
            doUpdate();
            while (inputLen > BUFFER_LENGTH) {
                System.arraycopy(in, dPos, xBuf, 0, BUFFER_LENGTH);
                inputLen -= BUFFER_LENGTH;
                dPos += BUFFER_LENGTH;
                doUpdate();
            }
        }

        System.arraycopy(in, dPos, xBuf, xBufOff, inputLen);
        xBufOff += inputLen;
    }

    public byte[] doFinal(byte[] in) {
        this.update(in);
        return this.doFinal();
    }

    public byte[] doFinal() {
        byte[] B = new byte[BLOCK_LENGTH];
        byte[] buffer = new byte[xBufOff];
        System.arraycopy(xBuf, 0, buffer, 0, buffer.length);
        byte[] tmp = SM3.padding(buffer, cntBlock);
        for (int i = 0; i < tmp.length; i += BLOCK_LENGTH) {
            System.arraycopy(tmp, i, B, 0, B.length);
            doHash(B);
        }
        return V;
    }

    public void update(byte in) {
        byte[] buffer = new byte[] { in };
        update(buffer, 0, 1);
    }

    public int getDigestSize() {
        return BYTE_LENGTH;
    }

    public String getKey(String inputStr) {
        byte[] md = new byte[32];
        byte[] msg1 = inputStr.getBytes();
        SM3Digest sm3 = new SM3Digest();
        sm3.update(msg1, 0, msg1.length);
        sm3.doFinal(md, 0);

        char c = string2char(inputStr);
        String finalStr = convertKey(Hex.encodeHexString(md), c);

        String hahaStr = "";
        char[] arr = finalStr.toCharArray();
        for (int i = 0; i < arr.length / 2; i++) {
            int integer1 = arr[i];
            int integer2 = arr[arr.length - 1 - i];
            int integer = (integer1 + integer2) % 95 + 32;
            char ch = (char) integer;
            hahaStr += ch;
        }
        return hahaStr.substring(4, 20);
    }

    private void doUpdate() {
        byte[] B = new byte[BLOCK_LENGTH];
        for (int i = 0; i < BUFFER_LENGTH; i += BLOCK_LENGTH) {
            System.arraycopy(xBuf, i, B, 0, B.length);
            doHash(B);
        }
        xBufOff = 0;
    }

    private void doHash(byte[] B) {
        byte[] tmp = SM3.cf(V, B);
        System.arraycopy(tmp, 0, V, 0, V.length);
        cntBlock++;
    }

    private static char string2char(String string) {
        int n = 0;
        for (int i = 0; i < string.length(); i++) {
            n += string.charAt(i);
        }
        n = n % 95 + 32;
        return (char) n;
    }

    private static String convertKey(String inStr, char c) {
        char[] a = inStr.toCharArray();
        for (int i = 0; i < a.length; i++) {
            a[i] = (char) (a[i] ^ c);
        }
        String s = new String(a);
        return s;
    }

    public static void main(String[] args) {
        byte[] hash = new SM3Digest().doFinal("ererfeiisgod".getBytes());
        System.out.println(Hex.encodeHexString(hash));
        
        hash = new SM3Digest().doFinal("ererfeiisgod".getBytes());
        System.out.println(Hex.encodeHexString(hash));
    }
}
