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
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.params.MainNetParams;
import com.google.bitcoin.store.BlockStore;
import com.google.bitcoin.store.BlockStoreException;
import com.google.bitcoin.store.SPVBlockStore;
import com.google.bitcoin.store.UnreadableWalletException;

public class Foo {
    public static final Logger slf4jLogger = LoggerFactory.getLogger(Foo.class);
    public static void main(String[] args) {
        run();
    }

    public static void run() {
        MainNetParams netParams = new MainNetParams();
        Wallet wallet = get_wallet(netParams);
        File blockStoreFile = new File("ev3_spv_block_store");
        try {
            SPVBlockStore blockStore = new SPVBlockStore(netParams,
                                                         blockStoreFile);
            BlockChain blockChain = new BlockChain(netParams, wallet,
                                                   blockStore);
            PeerGroup peerGroup = new PeerGroup(netParams, blockChain);
            peerGroup.addWallet(wallet);
            peerGroup.setBloomFilterFalsePositiveRate(1.0);
            LocalPeer localPeer = new LocalPeer();
            peerGroup.addPeerDiscovery(localPeer);
            PeerEventListener listener = new TxListener();
            peerGroup.addEventListener(listener);
            slf4jLogger.info("Starting peerGroup ...");
            peerGroup.startAndWait();
        }
        catch (BlockStoreException e) {
            System.err.println("Caught BlockStoreException: " + e.getMessage());
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
    public final Logger slf4jLogger = LoggerFactory.getLogger(Foo.class);

    public List<Message> getData (Peer p, GetDataMessage m) {
        return null;
    }
    public void onBlocksDownloaded(Peer p, Block b, int i) {}
    public void onChainDownloadStarted(Peer arg0, int arg1) {}
    public void onPeerConnected(Peer arg0, int arg1) {}
    public void onPeerDisconnected(Peer arg0, int arg1) {}
    public Message onPreMessageReceived(Peer arg0, Message arg1) {
        return null;
    }
    public void onTransaction(Peer peer, Transaction tx) {
        slf4jLogger.info("Transaction received: " + tx +
                           " from peer " + peer);
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
