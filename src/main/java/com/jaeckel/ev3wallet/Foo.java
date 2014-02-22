package com.jaeckel.ev3wallet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;


import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import java.security.spec.ECField;
import java.security.spec.ECPoint;
import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.h2.security.BlockCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.bitcoin.core.*;
import com.google.bitcoin.net.AbstractTimeoutHandler;
import com.google.bitcoin.net.discovery.DnsDiscovery;
import com.google.bitcoin.net.discovery.PeerDiscovery;
import com.google.bitcoin.script.Script;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionInput;
import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.params.MainNetParams;
import com.google.bitcoin.store.BlockStore;
import com.google.bitcoin.store.BlockStoreException;
import com.google.bitcoin.store.SPVBlockStore;
import com.google.bitcoin.store.UnreadableWalletException;

import lejos.hardware.Button;
import lejos.hardware.lcd.LCD;

public class Foo implements Runnable {
    public static final Logger slf4jLogger = LoggerFactory.getLogger(Foo.class);
    public static void main(String[] args) {

        slf4jLogger.info("main()");

        LCD.clear();
        LCD.drawString("EV3 Wallet", 4, 3);


        new Thread(new Foo()).start();

        Button.waitForAnyPress();
        LCD.clear();
        LCD.refresh();

        System.exit(1);
    }

    public void run() {
        MainNetParams netParams = new MainNetParams();
        Wallet wallet = get_wallet(netParams);
        File blockStoreFile = new File("ev3_spv_block_store");
        long offset = 0; // 86400 * 30;
        try {
            SPVBlockStore blockStore = new SPVBlockStore(netParams,
                                                         blockStoreFile);
            File checkpointsFile = new File("checkpoints");
            FileInputStream stream = new FileInputStream(checkpointsFile);
            CheckpointManager.checkpoint(netParams, stream, blockStore,
                                         wallet.getEarliestKeyCreationTime() - offset);

            BlockChain blockChain = new BlockChain(netParams, wallet,
                                                   blockStore);
            PeerGroup peerGroup = new PeerGroup(netParams, blockChain);
            peerGroup.addWallet(wallet);
            peerGroup.setFastCatchupTimeSecs(wallet.getEarliestKeyCreationTime() - offset);
            peerGroup.setBloomFilterFalsePositiveRate(1.0);
            // LocalPeer localPeer = new LocalPeer();
            // peerGroup.addPeerDiscovery(localPeer);
            peerGroup.addPeerDiscovery(new DnsDiscovery(netParams));
            // InetAddress ia = InetAddress.getByName("2.221.132.213");
            // peerGroup.addAddress(new PeerAddress(ia, 8333));
            slf4jLogger.info("Starting peerGroup ...");
            peerGroup.startAndWait();
            PeerEventListener listener = new TxListener();
            peerGroup.addEventListener(listener);
        }
        catch (BlockStoreException e) {
            System.err.println("Caught BlockStoreException: " + e.getMessage());
        }
        catch (UnknownHostException x) {
            System.err.println("Caught UnknownHostException: " +
                               x.getMessage());
        }
        catch (FileNotFoundException c) {
            System.err.println("Caught BlockStoreException: " + c.getMessage());
        }
        catch (IOException ie) {
            System.err.println("Caught BlockStoreException: " +
                               ie.getMessage());
        }
    }

    public static Wallet get_wallet(MainNetParams netParams) {
        File walletFile = new File("ev3_spv_wallet_file");
        Wallet wallet;
        try {
            wallet = Wallet.loadFromFile(walletFile);
        } catch (UnreadableWalletException e) {
            wallet = new Wallet(netParams);
            ECKey key = new ECKey();
            wallet.addKey(key);
            try {
                wallet.saveToFile(walletFile);
            }
            catch (IOException a) {
                System.err.println("Caught IOException: " + a.getMessage());
            }
        }
        return wallet;
    }
}


class TxListener implements PeerEventListener {
    MainNetParams netParams = new MainNetParams();

    public final Logger slf4jLogger = LoggerFactory.getLogger(Foo.class);

    public List<Message> getData (Peer p, GetDataMessage m) {
        slf4jLogger.info("Message received: " + m);
        return null;
    }
    public void onBlocksDownloaded(Peer p, Block b, int i) {
        slf4jLogger.info("Block downloaded:: " + b);
    }
    public void onChainDownloadStarted(Peer arg0, int arg1) {
        slf4jLogger.info("blockchain download started.");
    }
    public void onPeerConnected(Peer arg0, int arg1) {
        slf4jLogger.info("Peer Connected.");
    }
    public void onPeerDisconnected(Peer arg0, int arg1) {
        slf4jLogger.info("Peer Disonnected.");
    }
    public Message onPreMessageReceived(Peer arg0, Message m) {
        slf4jLogger.info("PreMessage Received:: " + m);
        return null;
    }
    public void onTransaction(Peer peer, Transaction tx) {
        boolean validTx = true;
        String txHash = tx.getHashAsString();
        List<TransactionOutput> txOutputs = tx.getOutputs();
        for (TransactionOutput output : txOutputs) {
            TransactionInput input = output.getSpentBy();
            try {
                if (output.getValue().compareTo(output.getMinNonDustValue()) != 1) {
                    slf4jLogger.info("Output is dust!");
                    validTx = false;
                    break;
                }
                // input.verify();
            }
            catch (RuntimeException epicfail) {
                slf4jLogger.info("Transaction outpoint verification failed.");
                validTx = false;
            }
        }
        if (validTx) {
            for (TransactionOutput output : txOutputs) {
                Script script = new Script(output.getScriptBytes());
                Address address = script.getToAddress(netParams);
                // TODO: add check of TO address here to see if its to ours
                slf4jLogger.info("Output TO address: " + address.toString() +
                                 " is " + output.getValue());
            }
        } else {
            slf4jLogger.info("Invalid transaction.");
        }
    }
}

class LocalPeer implements PeerDiscovery {
    public InetSocketAddress[] getPeers (long timeoutValue,
                                         TimeUnit timeoutUnit) {
        InetSocketAddress localPeer = new InetSocketAddress("172.31.77.138",
                                                            8333);
        InetSocketAddress[] peers = new InetSocketAddress [] {localPeer};
        return peers;
    }
    public void shutdown () {}
}
