package code.ponfee.commons.export;

import java.util.List;

import code.ponfee.commons.io.Files;
import code.ponfee.commons.util.ObjectUtils;

/**
 * csv导出
 * @author fupf
 */
public class CsvExporter extends AbstractExporter {

    private StringBuilder csv;

    public CsvExporter() {
        this.csv = new StringBuilder(0x1000); // 初始容量4096
    }

    @Override
    public void build(Table table) {
        if (csv.length() > 0) {
            throw new UnsupportedOperationException("only support signle table");
        }
        if (table.getThead() == null || table.getThead().isEmpty()) {
            throw new IllegalArgumentException("thead can't be null");
        }

        // build table thead
        buildThead(table.getThead(), table.getMaxTheadLevel());

        if (ObjectUtils.isEmpty(table.getTobdy()) && ObjectUtils.isEmpty(table.getTfoot())) {
            csv.append(NO_RESULT_TIP);
            return;
        } 

        super.nonEmpty();

        // tbody---------------
        List<Object[]> tbody = table.getTobdy();
        if (tbody != null && !tbody.isEmpty()) {
            Object[] datas;
            for (int n = tbody.size() - 1, i = 0, j, m; i <= n; i++) {
                datas = tbody.get(i);
                for (m = datas.length - 1, j = 0; j <= m; j++) {
                    csv.append(datas[j]);
                    if (j < m) {
                        csv.append(",");
                    }
                }
                if (i < n) {
                    csv.append(Files.LINE_SEPARATOR); // 换行
                }
            }
            csv.append(Files.LINE_SEPARATOR);
        }

        // tfoot---------
        if (table.getTfoot() != null && table.getTfoot().length > 0) {

            if (table.getTfoot().length > table.getTotalLeafCount()) {
                throw new IllegalStateException("tfoot data length cannot more than total leaf count.");
            }

            int n = table.getTotalLeafCount(), m = table.getTfoot().length, mergeNum = n - m;
            for (int i = 0; i < mergeNum; i++) {
                if (i == mergeNum - 1) {
                    csv.append("合计");
                }
                csv.append(",");
            }
            for (int i = mergeNum; i < n; i++) {
                csv.append(table.getTfoot()[i - mergeNum]);
                if (i != n - 1) {
                    csv.append(",");
                }
            }

            csv.append(Files.LINE_SEPARATOR);
        }

    }

    @Override
    public Object export() {
        return csv.toString();
    }

    @Override
    public void close() {
        csv.setLength(0);
        csv = null;
    }

    private void buildThead(List<Thead> thead, int maxTheadLevel) {
        for (Thead cell : thead) {
            if (cell.isLeaf()) {
                csv.append(cell.getName()).append(",");
            }
        }
        csv.deleteCharAt(csv.length() - 1);
        csv.append(Files.LINE_SEPARATOR);
    }

}
