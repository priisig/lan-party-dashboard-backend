package com.lanparty.dashboard.server;

/** How to ask a game server for its player count. */
public enum QueryType {
    /** Valve A2S_INFO over UDP (CS2, GMod, TF2, …). */
    SOURCE,
    /** Minecraft Java Edition Server List Ping over TCP. */
    MINECRAFT,
    /** Quake 3 "getstatus" over UDP (CoD4, Urban Terror, …). */
    QUAKE3,
    NONE
}
