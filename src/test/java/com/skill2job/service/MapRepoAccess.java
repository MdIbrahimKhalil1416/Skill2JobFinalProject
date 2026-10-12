package com.skill2job.service;

import java.util.Map;

/** Public door to the package-private MapRepo for tests in other packages. */
public final class MapRepoAccess {
    private MapRepoAccess() { }
    public static <R> R of(Class<R> type, Map<String, Object> store) { return MapRepo.of(type, store); }
    public static <R> R of(Class<R> type) { return MapRepo.of(type); }
}
