package sqm.riichimahjong;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

public class Tile {
    private final TileSuit suit;
    private final int number; // 만/삭/통: 1~9, 자패: 1~7 (동남서북백발중)
    private final boolean isRed;

    public Tile(TileSuit suit, int number) {
        this(suit, number ,false);
    }

    public Tile(TileSuit suit, int number, boolean isRed) {
        this.suit = suit;
        this.number = number;
        this.isRed = isRed;
    }

    public TileSuit getSuit() {
        return suit;
    }

    public int getNumber() {
        return number;
    }

    // 임시로 텍스트 표시용 (나중에 이미지로 대체될 부분)
    public String getDisplayName() {
        String base = switch (suit) {
            case MAN -> number + "만";
            case SOU -> number + "삭";
            case PIN -> number + "통";
            case HONOR -> switch (number) {
                case 1 -> "동";
                case 2 -> "남";
                case 3 -> "서";
                case 4 -> "북";
                case 5 -> "백";
                case 6 -> "발";
                case 7 -> "중";
                default -> "?";
            };
        };
        return isRed ? "적" + base : base;
    }

    // 나중에 이미지 텍스처 경로를 만들 때 쓸 고유 ID
    public String getTextureId() {
        return (isRed ? "aka_" : "") + suit.name().toLowerCase() + "_" + number;
        // 예: "man_1", "honor_5" 등
    }

    public void writeToBuf(FriendlyByteBuf buf){
        buf.writeEnum(suit);
        buf.writeInt(number);
        buf.writeBoolean(isRed);
    }

    public static Tile readFromBuf(FriendlyByteBuf buf){
        TileSuit suit = buf.readEnum(TileSuit.class);
        int number = buf.readInt();
        boolean isRed = buf.readBoolean();
        return new Tile(suit,number,isRed);
    }

    public CompoundTag toNBT(){
        CompoundTag tag = new CompoundTag();
        tag.putString("suit",suit.name());
        tag.putInt("number",number);
        tag.putBoolean("isRed",isRed);
        return tag;
    }

    public static Tile fromNBT(CompoundTag tag){
        TileSuit suit = TileSuit.valueOf(tag.getString("suit"));
        int number = tag.getInt("number");
        boolean isRed = tag.getBoolean("isRed");
        return new Tile(suit,number,isRed);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Tile other)) return false;
        return suit == other.suit && number == other.number;
    }

    @Override
    public int hashCode() {
        return Objects.hash(suit, number);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public boolean isRed(){
        return isRed;
    }
}