package com.skill2job.service;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A tiny in-memory Spring Data repository for tests: save / findById / delete / findBy&lt;Property&gt;[IgnoreCase] /
 * existsBy&lt;Property&gt; / deleteBy&lt;Property&gt;. It stores the SAME object that was saved (like a document the
 * service later re-reads), so tests can inspect and edit what the service persisted.
 */
final class MapRepo {

    private MapRepo() { }

    @SuppressWarnings("unchecked")
    static <R> R of(Class<R> repoType, Map<String, Object> store) {
        return (R) Proxy.newProxyInstance(repoType.getClassLoader(), new Class<?>[]{repoType}, (proxy, m, args) -> {
            String n = m.getName();
            switch (n) {
                case "save": {
                    Object e = args[0];
                    String id = (String) call(e, "getId");
                    if (id == null) {
                        id = UUID.randomUUID().toString();
                        e.getClass().getMethod("setId", String.class).invoke(e, id);
                    }
                    synchronized (store) { store.put(id, e); }
                    return e;
                }
                case "findById":
                    synchronized (store) { return Optional.ofNullable(store.get((String) args[0])); }
                case "delete": {
                    synchronized (store) { store.remove((String) call(args[0], "getId")); }
                    return null;
                }
                case "count":
                    synchronized (store) { return (long) store.size(); }
                case "findAll":
                    synchronized (store) { return new ArrayList<>(store.values()); }
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return proxy == args[0];
                case "toString": return "MapRepo";
                default:
            }
            if (n.equals("saveAll")) {
                List<Object> out = new ArrayList<>();
                for (Object e : (Iterable<?>) args[0]) {
                    out.add(invoke(proxy, repoType, "save", e));
                }
                return out;
            }
            if (n.equals("findAllById")) {
                List<Object> out = new ArrayList<>();
                synchronized (store) {
                    for (Object id : (Iterable<?>) args[0]) { Object e = store.get((String) id); if (e != null) out.add(e); }
                }
                return out;
            }
            for (String prefix : new String[]{"findFirstBy", "findTop30By", "findTop20By", "findTop15By", "findBy", "countBy", "existsBy", "deleteBy"}) {
                if (n.startsWith(prefix)) {
                    String rest = n.substring(prefix.length());
                    int ob = rest.indexOf("OrderBy");
                    String orderBy = null;
                    if (ob >= 0) { orderBy = rest.substring(ob + 7); rest = rest.substring(0, ob); }
                    String[] props = rest.split("And(?=[A-Z])");
                    List<Object> found = new ArrayList<>();
                    synchronized (store) {
                        for (Object e : store.values()) {
                            boolean all = true;
                            int argIdx = 0;
                            for (int i = 0; i < props.length && all; i++) {
                                String prop = props[i];
                                if (prop.endsWith("True") || prop.endsWith("False")) {
                                    boolean wantTrue = prop.endsWith("True");
                                    String bp = prop.substring(0, prop.length() - (wantTrue ? 4 : 5));
                                    all = Boolean.valueOf(wantTrue).equals(call(e, getterFor(e, bp)));
                                    continue;
                                }
                                boolean ignoreCase = prop.endsWith("IgnoreCase");
                                if (ignoreCase) prop = prop.substring(0, prop.length() - 10);
                                boolean before = prop.endsWith("Before");
                                boolean after = prop.endsWith("After");
                                if (before || after) {
                                    prop = prop.substring(0, prop.length() - (before ? 6 : 5));
                                    @SuppressWarnings("unchecked") Comparable<Object> v = (Comparable<Object>) call(e, getterFor(e, prop));
                                    Object limit = args[argIdx++];
                                    all = v != null && (before ? v.compareTo(limit) < 0 : v.compareTo(limit) > 0);
                                    continue;
                                }
                                boolean in = prop.endsWith("In") && !prop.equals("In");
                                if (in) prop = prop.substring(0, prop.length() - 2);
                                Object v = call(e, getterFor(e, prop));
                                Object want = args[argIdx++];
                                if (in) {
                                    all = v != null && ((java.util.Collection<?>) want).contains(v);
                                } else {
                                    all = (v == null && want == null) || (v != null && want != null
                                            && (ignoreCase ? v.toString().equalsIgnoreCase(want.toString()) : v.equals(want)));
                                }
                            }
                            if (all) found.add(e);
                        }
                        if (prefix.equals("deleteBy")) {
                            for (Object e : found) store.remove((String) call(e, "getId"));
                            return null;
                        }
                    }
                    if (orderBy != null) {
                        boolean desc = orderBy.endsWith("Desc");
                        String prop = orderBy.substring(0, orderBy.length() - (desc ? 4 : 3));
                        final String getter = getterFor(found.isEmpty() ? new Object() : found.get(0), prop);
                        found.sort((x, y) -> {
                            try {
                                @SuppressWarnings("unchecked") Comparable<Object> a = (Comparable<Object>) call(x, getter);
                                Object b = call(y, getter);
                                int c = a == null ? (b == null ? 0 : -1) : (b == null ? 1 : a.compareTo(b));
                                return desc ? -c : c;
                            } catch (Exception ex) { throw new IllegalStateException(ex); }
                        });
                    }
                    if (prefix.equals("findFirstBy") && !found.isEmpty() && !Optional.class.isAssignableFrom(m.getReturnType())) {
                        return List.of(found.get(0));
                    }
                    if (prefix.equals("findFirstBy") && Optional.class.isAssignableFrom(m.getReturnType())) {
                        return found.stream().findFirst();
                    }
                    if (prefix.equals("existsBy")) return !found.isEmpty();
                    if (prefix.equals("countBy")) return (long) found.size();
                    if (Optional.class.isAssignableFrom(m.getReturnType())) return found.stream().findFirst();
                    if (prefix.startsWith("findTop")) {
                        int limit = Integer.parseInt(prefix.replaceAll("\\D", ""));
                        return found.size() > limit ? new ArrayList<>(found.subList(0, limit)) : found;
                    }
                    return found;
                }
            }
            throw new UnsupportedOperationException("MapRepo does not implement " + n);
        });
    }

    static <R> R of(Class<R> repoType) {
        return of(repoType, new LinkedHashMap<>());
    }

    private static String getterFor(Object e, String prop) {
        for (String p : new String[]{"get", "is"}) {
            try { e.getClass().getMethod(p + prop); return p + prop; } catch (NoSuchMethodException ignored) { }
        }
        return "get" + prop;
    }

    private static Object invoke(Object proxy, Class<?> type, String name, Object arg) throws Throwable {
        return Proxy.getInvocationHandler(proxy).invoke(proxy, type.getMethod(name, Object.class), new Object[]{arg});
    }

    private static Object call(Object target, String getter) throws Exception {
        Method g = target.getClass().getMethod(getter);
        return g.invoke(target);
    }
}
