package com.twok.addon.util;

import meteordevelopment.meteorclient.settings.*;

/** Tiny shortcuts for building settings. */
public final class S {
    private S() {}

    public static Setting<Boolean> bool(SettingGroup g, String name, String desc, boolean def) {
        return g.add(new BoolSetting.Builder().name(name).description(desc).defaultValue(def).build());
    }

    public static Setting<Double> dbl(SettingGroup g, String name, String desc, double def, double min, double max) {
        return g.add(new DoubleSetting.Builder().name(name).description(desc).defaultValue(def)
            .min(min).max(max).sliderRange(min, max).build());
    }

    public static Setting<Integer> in(SettingGroup g, String name, String desc, int def, int min, int max) {
        return g.add(new IntSetting.Builder().name(name).description(desc).defaultValue(def)
            .min(min).max(max).sliderRange(min, max).build());
    }

    public static <E extends Enum<E>> Setting<E> en(SettingGroup g, String name, String desc, E def) {
        return g.add(new EnumSetting.Builder<E>().name(name).description(desc).defaultValue(def).build());
    }
}
