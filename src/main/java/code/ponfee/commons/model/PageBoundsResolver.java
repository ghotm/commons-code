package code.ponfee.commons.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 分页解析器
 * @author fupf
 */
public final class PageBoundsResolver {

    private PageBoundsResolver() {}

    /**
     * 多个数据源查询结果分页
     * @param pageNum 页号
     * @param pageSize 页大小
     * @param subTotalCounts 各数据源查询结果集行总计
     * @return
     */
    public static List<PageBounds> resolve(int pageNum, int pageSize, long... subTotalCounts) {
        if (subTotalCounts == null || subTotalCounts.length == 0) return null;

        // 总记录数
        long totalCounts = 0;
        for (long subTotalCount : subTotalCounts) {
            totalCounts += subTotalCount;
        }
        if (totalCounts < 1) {
            return null;
        }

        // pageSize小于1时表示查询全部
        if (pageSize < 1) {
            List<PageBounds> bounds = new ArrayList<>();
            for (int i = 0; i < subTotalCounts.length; i++) {
                // index, subTotalCounts, offset=0, limit=subTotalCounts
                bounds.add(new PageBounds(i, subTotalCounts[i], 0, (int) subTotalCounts[i]));
            }
            return bounds;
        }

        // 合理化pageNum、offset的值
        if (pageNum < 1) {
            pageNum = 1;
        }
        long offset = (pageNum - 1) * pageSize;
        if (offset >= totalCounts) { // 超出总记录数，则取最后一页
            pageNum = (int) (totalCounts + pageSize - 1) / pageSize;
            offset = (pageNum - 1) * pageSize;
        }

        // 分页计算
        List<PageBounds> bounds = new ArrayList<>();
        long start = offset, end = start + pageSize, cursor = 0;
        for (int limit, i = 0; i < subTotalCounts.length; cursor += subTotalCounts[i], i++) {
            if (start >= cursor + subTotalCounts[i]) continue;

            offset = start - cursor;
            if (end > cursor + subTotalCounts[i]) {
                limit = (int) (cursor + subTotalCounts[i] - start);
                bounds.add(new PageBounds(i, subTotalCounts[i], offset, limit));
                start = cursor + subTotalCounts[i];
            } else {
                limit = (int) (end - start);
                bounds.add(new PageBounds(i, subTotalCounts[i], offset, limit));
                break;
            }
        }
        return bounds;
    }

    /**
     * 单个数据源查询结果分页
     * @param pageNum
     * @param pageSize
     * @param totalCounts
     * @return
     */
    public static PageBounds resolve(int pageNum, int pageSize, long totalCounts) {
        List<PageBounds> list = resolve(pageNum, pageSize, new long[] { totalCounts });

        if (list == null || list.isEmpty()) {
            return null;
        } else {
            return list.get(0);
        }
    }

    /**
     * 分页对象
     */
    public static final class PageBounds {
        private final int index; // 数据源下标（start 0）
        private final long total; // 总记录数
        private final long offset; // 偏移量（start 0）
        private final int limit; // 数据行数

        PageBounds(int index, long total, long offset, int limit) {
            this.index = index;
            this.total = total;
            this.offset = offset;
            this.limit = limit;
        }

        public int getIndex() {
            return index;
        }

        public long getTotal() {
            return total;
        }

        public long getOffset() {
            return offset;
        }

        public int getLimit() {
            return limit;
        }

        @Override
        public String toString() {
            return "PageBounds [index=" + index + ", total=" + total + ", offset=" + offset + ", limit=" + limit + "]";
        }
    }

    public static void main(String[] args) {
        System.out.println(resolve(11, 10, 101));

        System.out.println("\n==============================");
        System.out.println(resolve(7, 15, 80, 9, 7, 10));

        System.out.println("\n==============================");
        System.out.println(resolve(16, 10, 155, 100));

        System.out.println("\n==============================");
        System.out.println(resolve(20000, 10, 155));

        System.out.println("\n==============================");
        System.out.println(resolve(6, 55, 155));
    }
}
