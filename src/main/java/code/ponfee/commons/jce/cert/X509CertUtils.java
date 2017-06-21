package code.ponfee.commons.jce.cert;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x509.X509CertificateStructure;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.jce.X509Principal;
import org.bouncycastle.jce.provider.X509CRLObject;
import org.bouncycastle.jce.provider.X509CRLParser;
import org.bouncycastle.jce.provider.X509CertificateObject;
import org.bouncycastle.util.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import code.ponfee.commons.util.Bytes;

/**
 * 证书工具类
 * @author fupf
 */
public class X509CertUtils {

    private static Logger logger = LoggerFactory.getLogger(X509CertUtils.class);
    private static final String X509 = "X.509";
    private static final char ENDBOUNDARY[] = "-----END".toCharArray();
    private static final FastDateFormat DATE_FORMAT = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    /**
     * 根据证书二进制数据加载证书
     * @param certBase64
     * @return
     */
    public static X509Certificate loadX509Cert(byte[] bytes) {
        try {
            // RSA证书
            CertificateFactory cf = CertificateFactory.getInstance(X509);
            return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            // SM2证书
            ASN1InputStream input = null;
            try {
                if (isBase64(new ByteArrayInputStream(bytes))) {
                    // base64（pem）编码证书
                    bytes = base64ToBinary(new ByteArrayInputStream(bytes));
                }
                input = new ASN1InputStream(new ByteArrayInputStream(bytes));
                ASN1Sequence seq = (ASN1Sequence) input.readObject();
                X509CertificateStructure struct = new X509CertificateStructure(seq);

                // JDK1.5可以运行，并且可以获取SM2 publicKey
                // JDK1.6不行：Unknown named curve: 1.2.156.10197.1.301
                /*DERObject publicKey = struct.getSubjectPublicKeyInfo().getPublicKey();
                struct.getSubjectPublicKeyInfo().getPublicKeyData();
                byte[] encodedPublicKey = publicKey.getEncoded();
                byte[] eP = new byte[64];
                System.arraycopy(encodedPublicKey, 5, eP, 0, eP.length);*/

                return new X509CertificateObject(struct);
            } catch (Exception ex) {
                SecurityException exception = new SecurityException(e.getMessage() + ";" + ex.getMessage());
                StackTraceElement[] trace1 = e.getStackTrace();
                StackTraceElement[] trace2 = ex.getStackTrace();
                StackTraceElement[] trace0 = new StackTraceElement[trace1.length + trace2.length];
                System.arraycopy(trace1, 0, trace0, 0, trace1.length);
                System.arraycopy(trace2, 0, trace0, trace1.length, trace2.length);
                exception.setStackTrace(trace0);
                throw exception;
            } finally {
                if (input != null) try {
                    input.close();
                } catch (IOException ex) {
                    logger.error("关闭证书文件流失败", e);
                }
            }
        }
    }

    /**
     * 根据证书文件流加载证书
     * @param is
     * @return
     * @throws IOException
     */
    public static X509Certificate loadX509Cert(InputStream input) throws IOException {
        return loadX509Cert(IOUtils.toByteArray(input));
    }

    /**
     * 通过证书文件路径加载证书
     * @param certPath
     * @return
     * @throws IOException
     */
    public static X509Certificate loadX509Cert(String certPath) throws IOException {
        return loadX509Cert(IOUtils.toByteArray(new FileInputStream(certPath)));
    }

    /**
     * 根据证书base64编码串加载证书
     * @param certBase64
     * @return
     */
    public static X509Certificate loadX509CertFromBase64(String certBase64) {
        return loadX509Cert(Bytes.base64Decode(certBase64));
    }

    /**
     * 根据byte流获取吊销列表
     * @param bytes
     * @return
     */
    public static X509CRL loadX509Crl(byte[] bytes) {
        ByteArrayInputStream bais = null;
        try {
            bais = new ByteArrayInputStream(bytes);
            //构建X509工厂
            CertificateFactory cf = CertificateFactory.getInstance(X509);
            //生成X509格式的CRL对象并返回
            return (X509CRL) cf.generateCRL(bais);
        } catch (Exception e) {
            X509CRLParser parser = new X509CRLParser();
            try {
                ByteArrayInputStream in = new ByteArrayInputStream(bytes);
                parser.engineInit(in);
                return (X509CRLObject) parser.engineRead();
            } catch (Exception ex) {
                SecurityException iae = new SecurityException(e.getMessage() + "；"
                    + ex.getMessage());
                StackTraceElement[] se = e.getStackTrace();
                StackTraceElement[] sx = ex.getStackTrace();
                StackTraceElement[] s = new StackTraceElement[se.length + sx.length];
                System.arraycopy(se, 0, s, 0, se.length);
                System.arraycopy(sx, 0, s, se.length, sx.length);
                iae.setStackTrace(s);
                throw iae;
            }
        }
    }

    /**
     * 获取crl
     * @throws IOException
     */
    public static X509CRL loadX509Crl(InputStream is) throws IOException {
        return loadX509Crl(IOUtils.toByteArray(is));
    }

    /**
     * 获取crl
     * @throws IOException
     */
    public static X509CRL loadX509Crl(String filePath) throws IOException {
        return loadX509Crl(IOUtils.toByteArray(new FileInputStream(filePath)));
    }

    /**
     * 获取证书掉销实体
     * @param crlPath
     * @param certPath
     * @return
     * @throws IOException
     */
    public static X509CRLEntry getX509CrlEntry(String crlPath, String certPath) throws IOException {
        X509CRL crl = loadX509Crl(crlPath);
        X509Certificate cert = loadX509Cert(certPath);
        return crl.getRevokedCertificate(cert);
    }

    /**
     * 获取证书扩展项信息
     * @param cert
     * @param oid
     * @return
     */
    public static String getCertExtVal(X509Certificate cert, String oid) {
        byte[] bytes = cert.getExtensionValue(oid);
        String reuslt = null;
        if (null != bytes && bytes.length > 0) {
            /* String result = new String(bytes); if (result.charAt(0) == 12) result = result.substring(2); */
            String value = new String(bytes);
            reuslt = value.substring(4, value.length());
        }
        return reuslt;
    }

    /**
     * 查询证书信息
     * @param cert
     * @param info
     * @return
     * @throws IOException
     */
    public static String getCertInfo(X509Certificate cert, X509CertInfo info) throws IOException {
        try {
            switch (info) {
                case VERSION:
                    return cert.getVersion() + "";
                case CERT_SN:
                    return cert.getSerialNumber().toString(16).toUpperCase();
                case ALG_NAME:
                    return cert.getSigAlgName();
                case START_TM:
                    return DATE_FORMAT.format(cert.getNotBefore());
                case END_TM:
                    return DATE_FORMAT.format(cert.getNotAfter());
                case SUBJECT_DN:
                    return new X509Principal(cert.getSubjectX500Principal().getEncoded()).getName();
                case ISSUER_DN:
                    return new X509Principal(cert.getIssuerX500Principal().getEncoded()).getName();
                case PUBLIC_KEY:
                    return Bytes.base64Encode(cert.getPublicKey().getEncoded());
                case USAGE:
                    if (cert.getKeyUsage()[0]) return "signature";
                    else if (cert.getKeyUsage()[3]) return "encipherment";
                    else return null;
                case SUBJECT_C:
                case SUBJECT_CN:
                case SUBJECT_L:
                case SUBJECT_O:
                case SUBJECT_OU:
                case SUBJECT_ST:
                    return parseCertDN(new X509Principal(cert.getSubjectX500Principal().getEncoded()).getName(), info);
                case ISSUER_C:
                case ISSUER_CN:
                case ISSUER_L:
                case ISSUER_O:
                case ISSUER_OU:
                case ISSUER_ST:
                    return parseCertDN(new X509Principal(cert.getIssuerX500Principal().getEncoded()).getName(), info);
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 查询证书信息
     * @param crl
     * @param info
     * @return
     */
    public static String parseCertDN(X509Principal x509p, X509CertInfo info) {
        switch (info) {
            case SUBJECT_C:
            case SUBJECT_CN:
            case SUBJECT_L:
            case SUBJECT_O:
            case SUBJECT_OU:
            case SUBJECT_ST:

            case ISSUER_C:
            case ISSUER_CN:
            case ISSUER_L:
            case ISSUER_O:
            case ISSUER_OU:
            case ISSUER_ST:
                return parseCertDN(x509p.getName(), info);
            default:
                throw new IllegalArgumentException("暂不支持其它属性");
        }
    }

    /**
     * 筛选证书主题信息
     * @param certDN
     * @param ci
     * @return
     */
    private static String parseCertDN(String certDN, X509CertInfo ci) {
        String type = ci.attr() + "=";
        String[] split = certDN.split(",");
        for (String x : split) {
            if (x.contains(type)) {
                x = x.trim();
                return x.substring(type.length());
            }
        }
        return null;
    }

    /**
     * 解析PKCS7（SM2证书）
     * @param p7bytes
     * @return
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseP7(byte[] p7bytes) {
        try {
            Map<String, Object> result = new HashMap<String, Object>();
            CMSSignedData cms = new CMSSignedData(p7bytes);
            result.put("content", cms.getSignedContent().getContent()); // 原文

            Store certStore = cms.getCertificates();
            SignerInformationStore signerStore = cms.getSignerInfos();
            Collection<SignerInformation> signers = signerStore.getSigners();
            //List<X509CertificateObject> certs = new ArrayList<X509CertificateObject>(); // 报错
            X509CertificateObject[] certs = new X509CertificateObject[signers.size()];
            int i = 0;
            for (SignerInformation signer : signers) {
                Collection<X509CertificateHolder> certChain = certStore.getMatches(signer.getSID());
                X509CertificateStructure cert = certChain.iterator().next().toASN1Structure();
                certs[i++] = new X509CertificateObject(cert);
            }
            result.put("certs", certs);
            result.put("signers", cms.getSignerInfos().getSigners()); // 签名值（支持多人签名）
            return result;
        } catch (Exception e) {
            throw new SecurityException("解析P7S异常", e);
        }
    }

    // --------------以下是解析Base64(pem)格式证书所用到的方法 start----------------- //
    private static boolean isBase64(InputStream inputstream)
        throws IOException {
        try {
            if (!inputstream.markSupported()) {
                byte abyte0[] = getTotalBytes(new BufferedInputStream(inputstream));
                inputstream = new ByteArrayInputStream(abyte0);
            }

            if (inputstream.available() >= 10) {
                inputstream.mark(10);
                int i = inputstream.read();
                int j = inputstream.read();
                int k = inputstream.read();
                int l = inputstream.read();
                int i1 = inputstream.read();
                int j1 = inputstream.read();
                int k1 = inputstream.read();
                int l1 = inputstream.read();
                int i2 = inputstream.read();
                int j2 = inputstream.read();
                inputstream.reset();
                return i == 45 && j == 45 && k == 45 && l == 45 && i1 == 45 && j1 == 66 && k1 == 69
                    && l1 == 71 && i2 == 73 && j2 == 78;
            } else {
                return false;
            }
        } finally {
            if (inputstream != null) try {
                inputstream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            inputstream = null;
        }
    }

    private static byte[] getTotalBytes(InputStream inputstream)
        throws IOException {
        byte abyte0[] = new byte[8192];
        ByteArrayOutputStream baos = new ByteArrayOutputStream(2048);
        baos.reset();
        int i;
        while ((i = inputstream.read(abyte0, 0, abyte0.length)) != -1)
            baos.write(abyte0, 0, i);
        return baos.toByteArray();
    }

    private static byte[] base64ToBinary(InputStream inputstream)
        throws IOException {
        try {
            long l = 0L;
            inputstream.mark(inputstream.available());
            BufferedInputStream bufferedinputstream = new BufferedInputStream(inputstream);
            BufferedReader bufferedreader = new BufferedReader(new InputStreamReader(bufferedinputstream, "ASCII"));
            String s;
            if ((s = readLine(bufferedreader)) == null || !s.startsWith("-----BEGIN")) throw new IOException("Unsupported encoding");
            l += s.length();
            StringBuffer stringbuffer = new StringBuffer();
            for (; (s = readLine(bufferedreader)) != null && !s.startsWith("-----END"); stringbuffer.append(s))
                ;

            if (s == null) {
                throw new IOException("Unsupported encoding");
            } else {
                l += s.length();
                l += stringbuffer.length();
                inputstream.reset();
                inputstream.skip(l);
                return Bytes.base64Decode(stringbuffer.toString());
            }
        } finally {
            if (inputstream != null) try {
                inputstream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            inputstream = null;
        }
    }

    private static String readLine(BufferedReader bufferedreader)
        throws IOException {
        int j = 0;
        boolean flag = true;
        boolean flag1 = false;
        StringBuffer stringbuffer = new StringBuffer(80);
        int i;
        do {
            i = bufferedreader.read();
            if (flag && j < ENDBOUNDARY.length) flag = (char) i == ENDBOUNDARY[j++];
            if (!flag1) flag1 = flag && j == ENDBOUNDARY.length;
            stringbuffer.append((char) i);
        } while (i != -1 && i != 10 && i != 13);
        if (!flag1 && i == -1) return null;
        if (i == 13) {
            bufferedreader.mark(1);
            int k = bufferedreader.read();
            if (k == 10) stringbuffer.append((char) i);
            else bufferedreader.reset();
        }
        return stringbuffer.toString();
    }
    // --------------以上是解析Base64(pem)格式证书所用到的方法 end----------------- //

}