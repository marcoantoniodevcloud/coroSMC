package com.garethevans.church.opensongtablet.songprocessing;

import android.graphics.Color;

import com.garethevans.church.opensongtablet.R;

import java.util.Locale;

public enum LiturgicalSeason {
    ADVENT("advent", R.string.liturgical_advent, R.string.liturgical_advent_badge,
            Color.rgb(239, 230, 245), Color.rgb(75, 40, 109), LiturgicalColor.PURPLE),
    LENT("lent", R.string.liturgical_lent, R.string.liturgical_lent_badge,
            Color.rgb(239, 230, 245), Color.rgb(75, 40, 109), LiturgicalColor.PURPLE),
    ORDINARY("ordinary", R.string.liturgical_ordinary, R.string.liturgical_ordinary_badge,
            Color.rgb(229, 241, 229), Color.rgb(40, 90, 52), LiturgicalColor.GREEN),
    CHRISTMAS("christmas", R.string.liturgical_christmas, R.string.liturgical_christmas_badge,
            Color.rgb(255, 241, 201), Color.rgb(112, 83, 0), LiturgicalColor.GOLD),
    EASTER("easter", R.string.liturgical_easter, R.string.liturgical_easter_badge,
            Color.rgb(255, 241, 201), Color.rgb(112, 83, 0), LiturgicalColor.GOLD),
    TRIDUUM("triduum", R.string.liturgical_triduum, R.string.liturgical_triduum_badge,
            Color.rgb(248, 225, 223), Color.rgb(122, 40, 40), LiturgicalColor.RED),
    PENTECOST("pentecost", R.string.liturgical_pentecost, R.string.liturgical_pentecost_badge,
            Color.rgb(248, 225, 223), Color.rgb(122, 40, 40), LiturgicalColor.RED),
    GOOD_FRIDAY("good_friday", R.string.liturgical_good_friday, R.string.liturgical_good_friday_badge,
            Color.rgb(248, 225, 223), Color.rgb(122, 40, 40), LiturgicalColor.RED),
    FUNERALS("funerals", R.string.liturgical_funerals, R.string.liturgical_funerals_badge,
            Color.rgb(239, 230, 245), Color.rgb(75, 40, 109), LiturgicalColor.PURPLE),
    JOY("joy", R.string.liturgical_joy, R.string.liturgical_joy_badge,
            Color.rgb(250, 229, 235), Color.rgb(130, 48, 75), LiturgicalColor.PINK),
    MARIAN("marian", R.string.liturgical_marian, R.string.liturgical_marian_badge,
            Color.rgb(225, 237, 250), Color.rgb(35, 76, 125), LiturgicalColor.BLUE);

    public enum LiturgicalColor {
        PURPLE("purple", R.string.liturgical_color_purple),
        GREEN("green", R.string.liturgical_color_green),
        GOLD("gold", R.string.liturgical_color_gold),
        RED("red", R.string.liturgical_color_red),
        PINK("pink", R.string.liturgical_color_pink),
        BLUE("blue", R.string.liturgical_color_blue);

        private final String value;
        private final int nameResource;

        LiturgicalColor(String value, int nameResource) {
            this.value = value;
            this.nameResource = nameResource;
        }

        public String getValue() {
            return value;
        }

        public int getNameResource() {
            return nameResource;
        }

        public static LiturgicalColor fromValue(String value) {
            if (value == null) {
                return null;
            }
            for (LiturgicalColor color : values()) {
                if (color.value.equalsIgnoreCase(value.trim())) {
                    return color;
                }
            }
            return null;
        }
    }

    private final String value;
    private final int nameResource;
    private final int badgeResource;
    private final int badgeBackgroundColor;
    private final int textColor;
    private final LiturgicalColor liturgicalColor;

    LiturgicalSeason(String value, int nameResource, int badgeResource,
                     int badgeBackgroundColor, int textColor, LiturgicalColor liturgicalColor) {
        this.value = value;
        this.nameResource = nameResource;
        this.badgeResource = badgeResource;
        this.badgeBackgroundColor = badgeBackgroundColor;
        this.textColor = textColor;
        this.liturgicalColor = liturgicalColor;
    }

    public String getValue() {
        return value;
    }

    public int getNameResource() {
        return nameResource;
    }

    public int getBadgeResource() {
        return badgeResource;
    }

    public int getBadgeBackgroundColor() {
        return badgeBackgroundColor;
    }

    public int getTextColor() {
        return textColor;
    }

    public LiturgicalColor getLiturgicalColor() {
        return liturgicalColor;
    }

    public static LiturgicalSeason fromValue(String value) {
        if (value == null) {
            return ORDINARY;
        }

        switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "advent":
            case "advient":
            case "adviento":
                return ADVENT;
            case "lent":
            case "cuaresma":
                return LENT;
            case "christmas":
            case "navidad":
                return CHRISTMAS;
            case "easter":
            case "pascua":
                return EASTER;
            case "triduum":
            case "triduopascual":
            case "triduo_pascual":
                return TRIDUUM;
            case "pentecost":
            case "pentecostes":
            case "pentecostés":
                return PENTECOST;
            case "good_friday":
            case "good friday":
            case "viernes santo":
                return GOOD_FRIDAY;
            case "funerals":
            case "funeral":
            case "exequias":
            case "excequias":
                return FUNERALS;
            case "joy":
            case "alegria":
            case "alegría":
                return JOY;
            case "marian":
            case "mary":
            case "maria":
            case "maría":
                return MARIAN;
            case "ordinary":
            case "ordinario":
            case "tiempo ordinario":
            default:
                return ORDINARY;
        }
    }
}
