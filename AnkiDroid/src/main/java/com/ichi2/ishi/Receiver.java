package com.ichi2.ishi;

import android.content.Context;
import android.util.Log;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;

import org.jsoup.Jsoup;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

import timber.log.Timber;

public class Receiver extends PebbleKit.PebbleDataReceiver
{
    private static final UUID WATCHAPP_UUID = UUID.fromString("6c0bbc11-7214-4e08-9b45-806b5f1a07a4");
    private static final String WATCHAPP_FILENAME = "ishi.pbw";
    private static final String TAG = "Receiver";

    public static final int EASE_FAILED = 1;
    public static final int EASE_HARD = 2;
    public static final int EASE_MID = 3;
    public static final int EASE_EASY = 4;

    private static final int
            KEY_BUTTON = 0,
            BUTTON_UP = 0,
            BUTTON_SELECT = 1,
            BUTTON_DOWN = 2,

            KEY_VIBRATE = 1,

            KEY_ACTION = 2,
            ACTION_ANS = 0,
            ACTION_EASE = 1,
            ACTION_Q = 2,
            ACTION_DECK_SELECT = 3,

            INBOX_LIMIT = 126,

            KEY_DECKS = 3,
            KEY_QUESTION = 4,
            KEY_ANSWER = 5,
            KEY_EASE = 6;

    private Queue<Byte[]> messageQ;
    private static Card mCard;

    public Receiver()
    {
        super(WATCHAPP_UUID);
        messageQ = new LinkedList<Byte[]>();
    }

    private Collection getCol()
    {
        return AnkiDroidApp.getCol();
    }

    private void queueMessage(ArrayList<String> strs)
    {
        Byte[] sendBuffer = new Byte[INBOX_LIMIT];
        byte[] tBuf;
        int sendIndex = 0;
        for (String s: strs)
        {
            tBuf = s.getBytes();
            for (int i = 0; i < tBuf.length; i++)
            {
                sendBuffer[sendIndex++] = tBuf[i];
                if (sendIndex >= INBOX_LIMIT)
                {
                    messageQ.add(sendBuffer);
                    sendBuffer = new Byte[INBOX_LIMIT];
                    sendIndex = 0;
                }
            }
            sendBuffer[sendIndex++] = ';';
            if (sendIndex >= INBOX_LIMIT)
            {
                messageQ.add(sendBuffer);
                sendBuffer = new Byte[INBOX_LIMIT];
                sendIndex = 0;
            }
        }
    }

    @Override
    public void receiveData(Context context, int transactionId, PebbleDictionary data)
    {
        PebbleKit.sendAckToPebble(context, transactionId);

        Collection col;
        //testing purposes
        /*if (data.getInteger(KEY_BUTTON) != null)
        {
            final int button = data.getInteger(KEY_BUTTON).intValue();
            if (button == BUTTON_SELECT)
            {
                col = getCol();
                PebbleDictionary vib = new PebbleDictionary();
                vib.addString(KEY_VIBRATE, col.getDecks().allNames().get(0).toString());
                PebbleKit.sendDataToPebble(context, WATCHAPP_UUID, vib);
            }

        }*/

        if (data.getString(KEY_DECKS) != null)
        {
            final String deck = data.getString(KEY_DECKS);
            long did = getCol().getDecks().byName(deck).optLong("id");
            getCol().getDecks().select(did);
            Timber.i("Selected deck: " + deck + " with id: " + did);//getCol().getDecks().byName(deck));
            Timber.i("Sending question to pebble...");
            mCard = getCol().getSched().getCard();
            PebbleDictionary q = new PebbleDictionary();
            q.addString(KEY_QUESTION, Jsoup.parse(mCard.qSimple()).text());
            PebbleKit.sendDataToPebble(context, WATCHAPP_UUID, q);
        }

        if (data.getInteger(KEY_EASE) != null)
        {
            final int ease = data.getInteger(KEY_EASE).intValue();
            getCol().getSched().answerCard(mCard, ease);
            Timber.i("Sending question to pebble...");
            mCard = getCol().getSched().getCard();
            PebbleDictionary q = new PebbleDictionary();
            q.addString(KEY_QUESTION, Jsoup.parse(mCard.qSimple()).text());
            PebbleKit.sendDataToPebble(context, WATCHAPP_UUID, q);
        }

        if (data.getInteger(KEY_ACTION) != null)
        {
            final int action = data.getInteger(KEY_ACTION).intValue();
            Timber.d("Found an action intent: " + action);
            switch (action)
            {
                case ACTION_ANS:
                    PebbleDictionary a = new PebbleDictionary();
                    a.addString(KEY_ANSWER,Jsoup.parse(mCard.aSimple()).text());
                    PebbleKit.sendDataToPebble(context, WATCHAPP_UUID, a);
                    break;
                case ACTION_EASE:
                    int eases = getCol().getSched().answerButtons(mCard);
                    PebbleDictionary e = new PebbleDictionary();
                    e.addInt32(KEY_EASE, eases);
                    Timber.i("Display " + eases + " ease levels.");
                    PebbleKit.sendDataToPebble(context, WATCHAPP_UUID, e);
                    break;
                case ACTION_Q:
                    Timber.i("Sending question to pebble...");
                    mCard = getCol().getSched().getCard();
                    PebbleDictionary q = new PebbleDictionary();
                    q.addString(KEY_QUESTION, Jsoup.parse(mCard.qSimple()).text());
                    PebbleKit.sendDataToPebble(context, WATCHAPP_UUID, q);
                    break;
                case ACTION_DECK_SELECT:
                    Timber.i("Asking for decks...");
                    ArrayList<String> strs = getCol().getDecks().allNames();
                    PebbleDictionary message = new PebbleDictionary();

                    String msg = "";
                    for (String s: strs)
                    {
                        if (s.equals("Default")) continue;
                        msg += s + ";";
                    }
                    message.addString(KEY_DECKS, msg);//strs.get(0));// + ";" + strs.get(1));
                    //Timber.i("Send a string of size: " + strs.get(0).length());
                    PebbleKit.sendDataToPebble(context, WATCHAPP_UUID, message);
                    break;
                default:
                    Log.d(TAG, "Unrecognized action.");
            }
        }

    }

}