package sqm.riichimahjong;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TileDeck {

    public static List<Tile> generateFullDeck() {
        List<Tile> deck = new ArrayList<>();

        // 만수, 삭수, 통수: 각 1~9번, 각 4장씩
        for (TileSuit suit : new TileSuit[]{TileSuit.MAN, TileSuit.SOU, TileSuit.PIN}) {
            for (int number = 1; number <= 9; number++) {
                for (int copy = 0; copy < 4; copy++) {
                    boolean isRed = (number == 5 && copy == 0);
                    deck.add(new Tile(suit, number, isRed));
                }
            }
        }

        // 자패: 1~7번(동남서북백발중), 각 4장씩
        for (int number = 1; number <= 7; number++) {
            for (int copy = 0; copy < 4; copy++) {
                deck.add(new Tile(TileSuit.HONOR, number));
            }
        }

        return deck; // 총 136장
    }

    public static List<Tile> shuffledDeck() {
        List<Tile> deck = generateFullDeck();
        Collections.shuffle(deck);
        return deck;
    }
}