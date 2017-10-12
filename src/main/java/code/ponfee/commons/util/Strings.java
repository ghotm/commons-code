package code.ponfee.commons.util;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.CRC32;

import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.Lists;

import code.ponfee.commons.math.Numbers;

/**
 * 字符串工具类
 * @author fupf
 */
public class Strings {

    private static final String FOLDER_SEPARATOR = "/";
    private static final String WINDOWS_FOLDER_SEPARATOR = "\\";
    private static final String TOP_PATH = "..";
    private static final String CURRENT_PATH = ".";

    public static String mask(String text, String regex, String replacement) {
        if (text == null) return null;
        return text.replaceAll(regex, replacement);
    }

    /**
     * 遮掩
     * @param text
     * @param start
     * @param len
     * @return
     */
    public static String mask(String text, int start, int len) {
        if (len < 1 || StringUtils.isEmpty(text) || text.length() < start) {
            return text;
        }
        if (start < 0) {
            start = 0;
        }
        if (text.length() < start + len) {
            len = text.length() - start;
        }
        int end = text.length() - start - len;
        String regex = "(\\w{" + start + "})\\w{" + len + "}(\\w{" + end + "})";
        return mask(text, regex, "$1" + StringUtils.repeat("*", len) + "$2");
    }

    /**
     * 字符串转long
     * @param str
     * @param charset
     * @return
     */
    public static long crc32(String str, String charset) {
        CRC32 crc32 = new CRC32();
        crc32.update(str.getBytes(Charset.forName(charset)));
        return crc32.getValue();
    }

    public static long crc32(String str) {
        return crc32(str, "UTF-8");
    }

    /**
     * 字符串分片
     * @param str
     * @param segment
     * @return
     */
    public static String[] slice(String str, int segment) {
        int[] array = Numbers.sharding(str.length(), segment);
        String[] result = new String[array.length];
        for (int j = 0, i = 0; i < array.length; i++) {
            result[i] = str.substring(j, (j += array[i]));
        }
        return result;
    }

    /**
     * 删除指定字符串中任意的字符<p>
     * deleteAny("hello world", "eo")  ->  hll wrld
     * @param inString       原字符串
     * @param charsToDelete  指定要删除的字符
     * @return               删除后的字符
     */
    public static String deleteAny(String inString, String charsToDelete) {
        if (StringUtils.isEmpty(inString) || StringUtils.isEmpty(charsToDelete)) {
            return inString;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < inString.length(); i++) {
            char c = inString.charAt(i);
            if (charsToDelete.indexOf(c) == -1) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 以分隔符拆分字符串为字符串数组
     * @param str         待拆分字符串
     * @param delimiter   分隔符
     * @return
     */
    public static String[] split(String str, String delimiter) {
        return split(str, delimiter, null);
    }

    /**
     * 以分隔符拆分字符串为字符串数组<p>
     * split("hello world", "l", "eo")  ->  [h,, wr,d]
     * @param str            待拆分字符串
     * @param delimiter      分隔符
     * @param charsToDelete  待删除的字符串
     * @return
     */
    public static String[] split(String str, String delimiter, String charsToDelete) {
        if (str == null) {
            return new String[0];
        }
        if (delimiter == null) {
            return new String[] { str };
        }
        List<String> result = new ArrayList<String>();
        if ("".equals(delimiter)) {
            for (int i = 0; i < str.length(); i++) {
                result.add(deleteAny(str.substring(i, i + 1), charsToDelete));
            }
        } else {
            int pos = 0;
            int delPos;
            while ((delPos = str.indexOf(delimiter, pos)) != -1) {
                result.add(deleteAny(str.substring(pos, delPos), charsToDelete));
                pos = delPos + delimiter.length();
            }
            if (str.length() > 0 && pos <= str.length()) {
                // Add rest of String, but not in case of empty input.
                result.add(deleteAny(str.substring(pos), charsToDelete));
            }
        }
        return toStringArray(result);
    }

    /**
     * '?' Matches any single character.
     * '*' Matches any sequence of characters (including the empty sequence).
     * 
     * isMatch("aa","a")       = false
     * isMatch("aa","aa")      = true
     * isMatch("aaa","aa")     = false
     * isMatch("aa", "*")      = true
     * isMatch("aa", "a*")     = true
     * isMatch("ab", "?*")     = true
     * isMatch("aab", "c*a*b") = false
     * @param s characters
     * @param p pattern
     * @return match result: true|false
     */
    public static boolean isMatch(String s, String p) {
        int idxs = 0, idxp = 0, idxstar = -1, idxmatch = 0;
        while (idxs < s.length()) {
            // 当两个指针指向完全相同的字符时，或者p中遇到的是?时
            if (idxp < p.length() && (s.charAt(idxs) == p.charAt(idxp) || p.charAt(idxp) == '?')) {
                idxp++;
                idxs++;
                // 如果字符不同也没有?，但在p中遇到是*时，我们记录下*的位置，但不改变s的指针
            } else if (idxp < p.length() && p.charAt(idxp) == '*') {
                idxstar = idxp;
                idxp++;
                //遇到*后，我们用idxmatch来记录*匹配到的s字符串的位置，和不用*匹配到的s字符串位置相区分
                idxmatch = idxs;
                // 如果字符不同也没有?，p指向的也不是*，但之前已经遇到*的话，我们可以从idxmatch继续匹配任意字符
            } else if (idxstar != -1) {
                // 用上一个*来匹配，那我们p的指针也应该退回至上一个*的后面
                idxp = idxstar + 1;
                // 用*匹配到的位置递增
                idxmatch++;
                // s的指针退回至用*匹配到位置
                idxs = idxmatch;
            } else {
                return false;
            }
        }
        // 因为1个*能匹配无限序列，如果p末尾有多个*，我们都要跳过
        while (idxp < p.length() && p.charAt(idxp) == '*') {
            idxp++;
        }
        // 如果p匹配完了，说明匹配成功
        return idxp == p.length();
    }

    /**
     * 文件路径规范化，如“path/..”内部的点号
     * 注意：windows的文件分隔符“\”会替换为“/”
     * @param 文件路径
     * @return 规范的文件路径 
     */
    public static String cleanPath(String path) {
        if (path == null) return null;

        String pathToUse = replace(path, WINDOWS_FOLDER_SEPARATOR, FOLDER_SEPARATOR);

        // Strip prefix from path to analyze, to not treat it as part of the
        // first path element. This is necessary to correctly parse paths like
        // "file:core/../core/io/Resource.class", where the ".." should just
        // strip the first "core" directory while keeping the "file:" prefix.
        int prefixIndex = pathToUse.indexOf(":");
        String prefix = "";
        if (prefixIndex != -1) {
            prefix = pathToUse.substring(0, prefixIndex + 1);
            if (prefix.contains("/")) {
                prefix = "";
            } else {
                pathToUse = pathToUse.substring(prefixIndex + 1);
            }
        }
        if (pathToUse.startsWith(FOLDER_SEPARATOR)) {
            prefix = prefix + FOLDER_SEPARATOR;
            pathToUse = pathToUse.substring(1);
        }

        String[] pathArray = split(pathToUse, FOLDER_SEPARATOR);
        List<String> pathElements = new LinkedList<String>();
        int tops = 0;

        for (int i = pathArray.length - 1; i >= 0; i--) {
            String element = pathArray[i];
            if (CURRENT_PATH.equals(element)) {
                // Points to current directory - drop it.
            } else if (TOP_PATH.equals(element)) {
                // Registering top path found.
                tops++;
            } else {
                if (tops > 0) {
                    // Merging path element with element corresponding to top path.
                    tops--;
                } else {
                    // Normal path element found.
                    pathElements.add(0, element);
                }
            }
        }

        // Remaining top paths need to be retained.
        for (int i = 0; i < tops; i++) {
            pathElements.add(0, TOP_PATH);
        }

        return prefix + join(pathElements, FOLDER_SEPARATOR);
    }

    /**
     * 集合拼接为字符串<p>
     * join(Lists.newArrayList("a","b","c"), ",", "(", ")") -> (a),(b),(c)
     * @param coll     集合对象
     * @param delim    分隔符
     * @param prefix   每个元素添加的前缀
     * @param suffix   每个元素添加的后缀
     * @return
     */
    public static String join(Collection<?> coll, String delim, String prefix, String suffix) {
        if (coll == null || coll.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Iterator<?> it = coll.iterator();
        while (it.hasNext()) {
            sb.append(prefix).append(it.next()).append(suffix);
            if (it.hasNext()) {
                sb.append(delim);
            }
        }
        return sb.toString();
    }

    /**
     * 集合拼接为字符串
     * @param coll    集合对象
     * @param delim   分隔符
     * @return
     */
    public static String join(Collection<?> coll, String delim) {
        return join(coll, delim, "", "");
    }

    /**
     * 字符串替换<p>
     * replace("hello world", "o", "-")  ->  hell- w-rld
     * @param inString
     * @param oldPattern
     * @param newPattern
     * @return
     */
    public static String replace(String inString, String oldPattern, String newPattern) {
        if (StringUtils.isEmpty(inString) || StringUtils.isEmpty(oldPattern) || newPattern == null) {
            return inString;
        }
        StringBuilder sb = new StringBuilder();
        int pos = 0; // our position in the old string
        int index = inString.indexOf(oldPattern);
        // the index of an occurrence we've found, or -1
        int patLen = oldPattern.length();
        while (index >= 0) {
            sb.append(inString.substring(pos, index));
            sb.append(newPattern);
            pos = index + patLen;
            index = inString.indexOf(oldPattern, pos);
        }
        sb.append(inString.substring(pos));
        // remember to append any characters to the right of a match
        return sb.toString();
    }

    /**
     * Collection转String[]
     * @param 集合
     * @return
     */
    public static String[] toStringArray(Collection<String> collection) {
        if (collection == null) return null;
        return collection.toArray(new String[collection.size()]);
    }

    /**
     * Enumeration转String[]
     * @param 枚举
     * @return
     */
    public static String[] toStringArray(Enumeration<String> enumeration) {
        if (enumeration == null) return null;
        return toStringArray(Collections.list(enumeration));
    }

    /**
     * 判断是否为空字符串
     * @param value
     * @return
     */
    public static boolean isEmpty(Object value) {
        return CharSequence.class.isInstance(value)
            && StringUtils.isEmpty((CharSequence) value);
    }

    /** 
     * 转换为下划线 
     * @param camelCaseName 下划线名
     * @return 
     */
    public static String underscoreName(String camelCaseName) {
        if (StringUtils.isEmpty(camelCaseName)) return camelCaseName;

        StringBuilder result = new StringBuilder();
        result.append(camelCaseName.substring(0, 1).toLowerCase());
        for (int i = 1; i < camelCaseName.length(); i++) {
            char ch = camelCaseName.charAt(i);
            if (Character.isUpperCase(ch)) {
                result.append("_");
                result.append(Character.toLowerCase(ch));
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    /** 
     * 转换为驼峰 
     * @param underscoreName 驼峰名
     * @return 
     */
    public static String camelCaseName(String underscoreName) {
        if (StringUtils.isEmpty(underscoreName)) return underscoreName;

        StringBuilder result = new StringBuilder();
        boolean flag = false;
        for (int i = 0; i < underscoreName.length(); i++) {
            char ch = underscoreName.charAt(i);
            if ("_".charAt(0) == ch) {
                flag = true;
            } else {
                if (flag) {
                    result.append(Character.toUpperCase(ch));
                    flag = false;
                } else {
                    result.append(ch);
                }
            }
        }
        return result.toString();
    }

    /**
     * 获取IEME校验码
     * @param str
     * @return  校验码
     */
    public static int iemeCode(String str) {
        int checkSum = 0;
        char[] chars = str.toCharArray();
        for (int i = 0, num; i < chars.length; i++) {
            num = chars[i] - '0'; // ascii to num  
            if (i % 2 == 0) {
                checkSum += num; // 1、将奇数位数字相加（从1开始计数）
            } else {
                num *= 2; // 2、将偶数位数字分别乘以2，分别计算个位数和十位数之和（从1开始计数）
                if (num < 10) checkSum += num;
                else checkSum += num - 9;
            }
        }
        return (10 - checkSum % 10) % 10;
    }

    /**
     * 如果为空则设置默认
     * @param str
     * @param defaultStr
     * @return
     */
    public static String setIfEmpty(String str, String defaultStr) {
        return StringUtils.isEmpty(str) ? defaultStr : str;
    }

    public static void main(String[] args) {
        System.out.println(deleteAny("hello world", "eo"));
        System.out.println(ObjectUtils.toString(split("hello world", "l", "eo")));
        System.out.println(replace("hello world", "o", "-"));
        System.out.println(join(Lists.newArrayList("a", "b", "c"), ",", "(", ")"));

        System.out.println(isMatch("ab * cdec", "ab * c"));
        System.out.println(isMatch("* ba", "* a"));
        System.out.println(isMatch("* a", "*"));
        System.out.println(isMatch("* a", "*"));
    }
}
