package com.vdom.core;

import java.util.ArrayList;

import com.vdom.api.Card;
import com.vdom.api.GameEvent;
import com.vdom.api.TreasureCard;
import com.vdom.api.VictoryCard;

public class TreasureCardImpl extends CardImpl implements TreasureCard {
    private static final long serialVersionUID = 1L;
    int value;
    boolean providePotion;
    
    public TreasureCardImpl(Cards.Type type, int cost, int value) {
        super(type, cost);
        this.value = value;
    }

    public TreasureCardImpl(Builder builder) {
        super(builder);
        value = builder.value;
        providePotion = builder.providePotion;
    }

    public static class Builder extends CardImpl.Builder {
        protected int value;
        protected boolean providePotion = false;

        public Builder(Cards.Type type, int cost, int value) {
            super(type, cost);
            this.value = value;
        }

        public Builder providePotion() {
            providePotion = true;
            return this;
        }

        public TreasureCardImpl build() {
            return new TreasureCardImpl(this);
        }

    }

    protected TreasureCardImpl() {
    }
    
    public TreasureCardImpl(String name, int cost, int value2, boolean costPotion, boolean b) {
    }

    public int getValue() {
        return value;
    }

    @Override
    public CardImpl instantiate() {
        checkInstantiateOK();
        TreasureCardImpl c = new TreasureCardImpl();
        copyValues(c);
        return c;
    }

    public boolean providePotion() {
        return providePotion;
    }

    protected void copyValues(TreasureCardImpl c) {
        super.copyValues(c);
        c.value = value;
        c.providePotion = providePotion;
    }

    @Override
    // return true if Treasure cards should be re-evaluated since might affect
    // coin play order
    public boolean playTreasure(MoveContext context) {
    	return playTreasure(context, false);
    }
    
    public boolean playTreasure(MoveContext context, boolean isClone) {
        boolean reevaluateTreasures = false;
        Player player = context.player;
        Game game = context.game;

        GameEvent event = new GameEvent(GameEvent.Type.PlayingCoin, (MoveContext) context);
        event.card = this;
        event.newCard = !isClone;
        game.broadcastEvent(event);

        if (this.numberTimesAlreadyPlayed == 0) {
        	player.hand.remove(this);
        	player.playedCards.add(this);
        }
        
        if (isAttack())
            attackPlayed(context, game, player);
        
        if (!isClone)
        {
            context.treasuresPlayedSoFar++;
        }
        
        context.addCoins(getValue());
        
        if (providePotion()) {
            context.potions++;
        }

        // Special cards
        if (equals(Cards.foolsGold)) {
            foolsGold(context);
        } else if (equals(Cards.philosophersStone)) {
            context.addCoins((player.getDeckSize() + player.getDiscardSize()) / 5);
        } else if (equals(Cards.diadem)) {
            context.addCoins(context.getActionsLeft());
        } else if (equals(Cards.copper)) {
            context.addCoins(context.coppersmithsPlayed);
        } else if (equals(Cards.bank)) {
            context.addCoins(treasuresInPlay(player.playedCards));
        } else if (equals(Cards.contraband)) {
            reevaluateTreasures = contraband(context, game, reevaluateTreasures);
        } else if (equals(Cards.loan) || equals(Cards.venture)) {
            reevaluateTreasures = loanVenture(context, player, game, reevaluateTreasures);
        } else if (equals(Cards.hornOfPlenty)) {
            hornOfPlenty(context, player, game);
        } else if (equals(Cards.illGottenGains)) {
            reevaluateTreasures = illGottenGains(context, player, reevaluateTreasures);
        } else if (equals(Cards.counterfeit)) {
        	reevaluateTreasures = counterfeit(context, game, reevaluateTreasures, player);
        } else if (equals(Cards.treasureTrove)) {
        	treasureTrove(context, player, game);
        } else if (equals(Cards.relic)) {
        	relic(context, player, game);
        } else if (equals(Cards.coinOfTheRealm)) {
        	putOnTavern(game, context, player);
        } else if (equals(Cards.spoils)) {
			if (!isClone) {
				// Return to the spoils pile
	            AbstractCardPile pile = game.getPile(this);
	            pile.addCard(player.playedCards.remove(player.playedCards.indexOf(this.getId())));
			}
        }

        return reevaluateTreasures;
    }

    protected int treasuresInPlay(CardList playedCards) {
    	int treasuresInPlay = 0;
        for (Card card : playedCards) {
 	       if (card instanceof TreasureCard) {
 	    	  treasuresInPlay++;
 	       }
        }
        return treasuresInPlay;
    }

    protected void foolsGold(MoveContext context) {
        context.foolsGoldPlayed++;
        if (context.foolsGoldPlayed > 1) {
            context.addCoins(3);
        }
    }

    protected boolean contraband(MoveContext context, Game game, boolean reevaluateTreasures) {
        context.buys++;
        Card cantBuyCard = game.getNextPlayer().controlPlayer.contraband_cardPlayerCantBuy(context);

        if (cantBuyCard != null && !context.cantBuy.contains(cantBuyCard)) {
            context.cantBuy.add(cantBuyCard);
            GameEvent e = new GameEvent(GameEvent.Type.CantBuy, (MoveContext) context);
            game.broadcastEvent(e);
        }
        return true;
    }

    protected boolean loanVenture(MoveContext context, Player player, Game game, boolean reevaluateTreasures) {
        ArrayList<Card> toDiscard = new ArrayList<Card>();
        TreasureCard treasureCardFound = null;
        GameEvent event = null;

        while (treasureCardFound == null) {
            Card draw = game.draw(player, -1);
            if (draw == null) {
                break;
            }

            event = new GameEvent(GameEvent.Type.CardRevealed, context);
            event.card = draw;
            game.broadcastEvent(event);

            if (draw instanceof TreasureCard) {
                treasureCardFound = (TreasureCard) draw;
            } else {
                toDiscard.add(draw);
            }
        }

        if (treasureCardFound != null) {
            if (equals(Cards.loan)) {
                if (player.controlPlayer.loan_shouldTrashTreasure(context, treasureCardFound)) {
                    player.trash(treasureCardFound, this, context);
                } else {
                    player.discard(treasureCardFound, this, null);
                }
            } else if (equals(Cards.venture)) {
                player.hand.add(treasureCardFound);
                treasureCardFound.playTreasure(context);
                reevaluateTreasures = true;
            }
        }

        while (!toDiscard.isEmpty()) {
            player.discard(toDiscard.remove(0), this, null);
        }
        return reevaluateTreasures;
    }

    protected boolean illGottenGains(MoveContext context, Player player, boolean reevaluateTreasures) {
        if (context.getCardsLeftInPile(Cards.copper) > 0) {
            if (player.controlPlayer.illGottenGains_gainCopper(context)) {
                player.gainNewCard(Cards.copper, this, context);
                reevaluateTreasures = true;
            }
        }
        return reevaluateTreasures;
    }

    protected void hornOfPlenty(MoveContext context, Player player, Game game) {
        GameEvent event;

        int maxCost = context.countUniqueCardsInPlayThisTurn();
        Card toObtain = player.controlPlayer.hornOfPlenty_cardToObtain(context, maxCost);
        if (toObtain != null) {
            // check cost
            if (toObtain.getCost(context) <= maxCost) {
                toObtain = game.takeFromPile(toObtain);
                // could still be null here if the pile is empty.
                if (toObtain != null) {
                    event = new GameEvent(GameEvent.Type.CardObtained, context);
                    event.card = toObtain;
                    event.responsible = this;
                    game.broadcastEvent(event);
                    
                    if (toObtain instanceof VictoryCard) {
                    	player.playedCards.remove(this);
                        player.trash(this, toObtain, context);
                        event = new GameEvent(GameEvent.Type.CardTrashed, context);
                        event.card = this;
                        game.broadcastEvent(event);
                    }
                }
            }
        }
    }
    
    protected boolean counterfeit(MoveContext context, Game game, boolean reevaluateTreasures, Player currentPlayer) {
        context.buys++;
        
    	TreasureCard treasure = currentPlayer.controlPlayer.counterfeit_cardToPlay(context);
    	
    	if (treasure != null) {
    		TreasureCardImpl cardToPlay = (TreasureCardImpl) treasure;
            cardToPlay.cloneCount = 2;
            for (int i = 0; i < cardToPlay.cloneCount;) {
                cardToPlay.numberTimesAlreadyPlayed = i++;
                cardToPlay.playTreasure(context, cardToPlay.numberTimesAlreadyPlayed == 0 ? false : true);
            }
            
            cardToPlay.cloneCount = 0;
            cardToPlay.numberTimesAlreadyPlayed = 0;    		
            
            // A counterfeited card will not count in the calculations of future cards that care about the number of treasures played (such as Bank)
            context.treasuresPlayedSoFar--; 
            
            if (!(treasure.equals(Cards.spoils) || treasure.equals(Cards.coinOfTheRealm))) {
                if (currentPlayer.playedCards.getLastCard().equals(treasure)) {
                	currentPlayer.playedCards.remove(treasure);
                	currentPlayer.trash(treasure, this, context);
                }
    		    }
    	}

        return true;

    }
    
    @Override
    public void isBuying(MoveContext context)
    {
        switch (this.controlCard.getType()) 
        {
        case Masterpiece:
            masterpiece(context);
            break;
        default:
            break;
        }
    }
    
    public void masterpiece(MoveContext context)
    {
        for (int i = 0; i < context.overpayAmount; ++i)
        {
            if(context.getPlayer().gainNewCard(Cards.silver, this.controlCard, context) == null) 
            {
                break;
            }
        }
    }

    /*Adventures*/
    protected void treasureTrove(MoveContext context, Player player, Game game) {
        context.getPlayer().gainNewCard(Cards.gold, this.controlCard, context); 
        context.getPlayer().gainNewCard(Cards.copper, this.controlCard, context);
    }    

    protected void relic(MoveContext context, Player player, Game game) {
        ArrayList<Player> playersToAttack = new ArrayList<Player>();
        for (Player targetPlayer : game.getPlayersInTurnOrder()) {
            if (targetPlayer != player && !Util.isDefendedFromAttack(game, targetPlayer, this.controlCard)) {
                playersToAttack.add(targetPlayer);
            }
        }

        for (Player targetPlayer : playersToAttack) {
            targetPlayer.attacked(this.controlCard, context);
            MoveContext targetContext = new MoveContext(context.game, targetPlayer);
        	targetPlayer.setMinusOneCardToken(true, targetContext);
        }
    }
    
    private void putOnTavern(Game game, MoveContext context, Player currentPlayer) {
        // counterfeit has here no effect since card is already put on tavern
        // Move to tavern mat
        if (this.controlCard.numberTimesAlreadyPlayed == 0) {
            currentPlayer.playedCards.remove(currentPlayer.playedCards.lastIndexOf((Card) this.controlCard));
            currentPlayer.tavern.add(this.controlCard);
            this.controlCard.stopImpersonatingCard();

            GameEvent event = new GameEvent(GameEvent.Type.CardSetAsideOnTavernMat, (MoveContext) context);
            event.card = this.controlCard;
            game.broadcastEvent(event);
        } else {
            // reset clone count
            this.controlCard.cloneCount = 1;
        }
    }
}
