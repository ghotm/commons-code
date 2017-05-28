package code.ponfee.commons.ws.adapter;

import java.util.Map;

import javax.xml.bind.annotation.adapters.XmlAdapter;

import com.google.common.collect.Maps;

import code.ponfee.commons.reflect.ClassUtils;
import code.ponfee.commons.ws.adapter.model.MapEntry;

/**
 * Map<K,V>转换器
 * @author fupf
 * @param <K>
 * @param <V>
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public abstract class MapAdapter<K, V> extends XmlAdapter<MapEntry[], Map<K, V>> {

    protected final Class<K> ktype;
    protected final Class<V> vtype;

    protected MapAdapter() {
        ktype = ClassUtils.getClassGenricType(this.getClass(), 0);
        vtype = ClassUtils.getClassGenricType(this.getClass(), 1);
    }

    public @Override MapEntry<K, V>[] marshal(Map<K, V> map) throws Exception {
        if (map == null) return null;

        MapEntry<K, V>[] entries = new MapEntry[map.size()];
        int i = 0;
        for (Map.Entry<K, V> entry : map.entrySet()) {
            entries[i++] = new MapEntry<K, V>(entry);
        }
        return entries;
    }

    public @Override Map<K, V> unmarshal(MapEntry[] entries) throws Exception {
        if (entries == null) return null;

        Map<K, V> map = Maps.newLinkedHashMap();
        for (MapEntry<K, V> e : entries) {
            map.put(e.getKey(), e.getValue());
        }
        return map;
    }

}
