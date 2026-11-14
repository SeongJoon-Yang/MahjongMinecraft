package sqm.riichimahjong;

import net.minecraft.resources.ResourceLocation;

public class ModPackets {
    public static final ResourceLocation DISCARD_TILE_PACKET = RiichiMahjong.id("discard_tile");
    public static final ResourceLocation UPDATE_HAND_PACKET = RiichiMahjong.id("update_hand");
    public static final ResourceLocation HAND_MAKE_UP = RiichiMahjong.id("hand_make_up");
    public static final ResourceLocation UPDATE_HAND_MAKE_UP = RiichiMahjong.id("update_hand_make_up");
    public static final ResourceLocation DECLARE_RIICHI_PACKET = RiichiMahjong.id("declare_riichi");
    public static final ResourceLocation DECLARE_PON_PACKET = RiichiMahjong.id("declare_pon");
    public static final ResourceLocation DECLARE_CHI_PACKET = RiichiMahjong.id("declare_chi");
    public static final ResourceLocation DECLARE_ANKAN_PACKET = RiichiMahjong.id("declare_ankan");
    public static final ResourceLocation DECLARE_DAIMINKAN_PACKET = RiichiMahjong.id("declare_daiminkan");
    public static final ResourceLocation DECLARE_KAKAN_PACKET = RiichiMahjong.id("declare_kakan");
    public static final ResourceLocation DECLARE_TSUMO_PACKET = RiichiMahjong.id("declare_tsumo");
    public static final ResourceLocation DECLARE_RON_PACKET = RiichiMahjong.id("declare_ron");
    public static final ResourceLocation PASS_CALL_PACKET = RiichiMahjong.id("pass_call");
    public static final ResourceLocation DECLARE_KYUUSHU_PACKET = RiichiMahjong.id("declare_kyuushu");
    public static final ResourceLocation NEXT_ROUND_PACKET = RiichiMahjong.id("next_round");
    public static final ResourceLocation MATCH_START_PACKET = RiichiMahjong.id("match_start");
    public static final ResourceLocation LOBBY_SEAT_PACKET = RiichiMahjong.id("lobby_seat");
    public static final ResourceLocation DEBUG_RESET_PACKET = RiichiMahjong.id("debug_reset");
}
