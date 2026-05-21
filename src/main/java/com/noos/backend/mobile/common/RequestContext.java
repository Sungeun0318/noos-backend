package com.noos.backend.mobile.common;

public final class RequestContext {

    private static final ThreadLocal<Holder> HOLDER = new ThreadLocal<>();

    private RequestContext() {
    }

    public static void setDeviceId(String deviceId) {
        Holder holder = getOrCreate();
        holder.deviceId = deviceId;
    }

    public static void setUser(Long userId, String deviceId) {
        Holder holder = getOrCreate();
        holder.userId = userId;
        holder.deviceId = deviceId;
    }

    public static String deviceId() {
        Holder holder = HOLDER.get();
        return holder == null ? null : holder.deviceId;
    }

    public static Long userId() {
        Holder holder = HOLDER.get();
        return holder == null ? null : holder.userId;
    }

    public static void clear() {
        HOLDER.remove();
    }

    private static Holder getOrCreate() {
        Holder holder = HOLDER.get();
        if (holder == null) {
            holder = new Holder();
            HOLDER.set(holder);
        }
        return holder;
    }

    private static final class Holder {
        private String deviceId;
        private Long userId;
    }
}
