# receiver.c looks these up by name with GetMethodID; R8 cannot see those calls.
-keep class com.fenpo.cast.receiver.NativeReceiver {
    native <methods>;
    boolean registerService(...);
    void unregisterService(...);
    void on*(...);
    boolean on*(...);
}
