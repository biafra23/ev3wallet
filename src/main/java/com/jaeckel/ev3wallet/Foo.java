package com.jaeckel.ev3wallet;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Block;
import com.google.bitcoin.core.BlockChain;
import com.google.bitcoin.core.CheckpointManager;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.GetDataMessage;
import com.google.bitcoin.core.Message;
import com.google.bitcoin.core.Peer;
import com.google.bitcoin.core.PeerEventListener;
import com.google.bitcoin.core.PeerGroup;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionInput;
import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.net.discovery.DnsDiscovery;
import com.google.bitcoin.net.discovery.PeerDiscovery;
import com.google.bitcoin.params.MainNetParams;
import com.google.bitcoin.script.Script;
import com.google.bitcoin.store.BlockStoreException;
import com.google.bitcoin.store.SPVBlockStore;
import com.google.bitcoin.store.UnreadableWalletException;
import com.google.bitcoin.uri.BitcoinURI;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lejos.hardware.Button;
import lejos.hardware.lcd.LCD;
import lejos.hardware.sensor.EV3IRSensor;
import lejos.hardware.port.AnalogPort;
import lejos.hardware.port.I2CPort;
import lejos.hardware.sensor.EV3SensorConstants;
import lejos.hardware.sensor.I2CSensor;
import lejos.hardware.port.Port;
import lejos.hardware.port.SensorPort;
import lejos.hardware.port.UARTPort;
import lejos.internal.ev3.EV3DeviceManager;
import lejos.robotics.SampleProvider;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;


public class Foo implements Runnable {

	private final static QRCodeWriter QR_WRITER = new QRCodeWriter();

	public static final Logger slf4jLogger = LoggerFactory.getLogger(Foo.class);
	public static void main(String[] args) {

		slf4jLogger.info("main()");

		LCD.clear();
		LCD.drawString("EV3 Wallet", 4, 3);

		new Thread(new Sensors()).start();
		new Thread(new Foo()).start();

		Button.waitForAnyPress();
		LCD.clear();
		LCD.refresh();

		System.exit(1);
	}

	public void run() {
		slf4jLogger.info("run()");

		MainNetParams netParams = new MainNetParams();
		Wallet wallet = get_wallet(netParams);
		slf4jLogger.info("Got wallet.");

		List<ECKey> keys = wallet.getKeys();
		Address address = keys.get(0).toAddress(netParams);

		LCD.clear();

		String uri = BitcoinURI.convertToBitcoinURI(address.toString(),
				new BigInteger("100000"), "EV3 Wallet", "");

		slf4jLogger.info("Wallet address: URI: " + uri);

		int widthHeight = 150;

		try {
			final BitMatrix result = QR_WRITER.encode(uri, BarcodeFormat.QR_CODE, widthHeight, widthHeight);

			for (int i = 0; i < widthHeight; i++) {
				for (int j = 0; j < widthHeight; j++) {

					boolean pixelSet = result.get(i, j);
					LCD.setPixel(i + 10 , j - 18, pixelSet ? 1 : 0);
				}
			}

		} catch (WriterException e) {
			slf4jLogger.error("Exception while encoding QRCode: ", e);
		}

		File blockStoreFile = new File("/home/root/ev3_spv_block_store");
		long offset = 0; // 86400 * 30;
		try {
			SPVBlockStore blockStore = new SPVBlockStore(netParams,
					blockStoreFile);
			slf4jLogger.info("SPVBlockStore instantiated");

			InputStream stream = getClass().getClassLoader().getResourceAsStream("checkpoints");
			CheckpointManager.checkpoint(netParams, stream, blockStore,
					wallet.getEarliestKeyCreationTime() - offset);

			BlockChain blockChain = new BlockChain(netParams, wallet,
					blockStore);
			PeerGroup peerGroup = new PeerGroup(netParams, blockChain);
			peerGroup.addWallet(wallet);
			peerGroup.setFastCatchupTimeSecs(wallet.getEarliestKeyCreationTime() - offset);
			peerGroup.setBloomFilterFalsePositiveRate(0.5);
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
			slf4jLogger.error("Caught BlockStoreException: ", e);
		}
		catch (UnknownHostException x) {
			slf4jLogger.error("Caught UnknownHostException: ", x);
		}
		catch (FileNotFoundException c) {
			slf4jLogger.error("Caught BlockStoreException: ", c);
		}
		catch (IOException ie) {
			slf4jLogger.error("Caught BlockStoreException: ", ie);
		}
	}

	public static Wallet get_wallet(MainNetParams netParams) {
		File walletFile = new File("/home/root/ev3_spv_wallet_file");
		Wallet wallet;
		try {
			wallet = Wallet.loadFromFile(walletFile);
		} catch (UnreadableWalletException e) {
			wallet = new Wallet(netParams);
			ECKey key = new ECKey();
			wallet.addKey(key);
			try {
				wallet.saveToFile(walletFile);
			} catch (IOException a) {
				slf4jLogger.error("Caught IOException: ", a);
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
		// slf4jLogger.info("Block downloaded:: " + b);
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
					// slf4jLogger.info("Output is dust!");
					validTx = false;
					break;
				}
				// input.verify();
			}
			catch (RuntimeException epicfail) {
				// slf4jLogger.info("Transaction outpoint verification failed.");
				validTx = false;
			}
		}
		for (TransactionOutput output : txOutputs) {
			Script script = new Script(output.getScriptBytes());
			Address address = script.getToAddress(netParams);
			// T	ODO: add check of TO address here to see if its to ours
			try {
				File walletFile = new File("/home/root/ev3_spv_wallet_file");
				Wallet wallet;
				wallet = Wallet.loadFromFile(walletFile);
				List<ECKey> keys = wallet.getKeys();
				Address ouraddress = keys.get(0).toAddress(netParams);
				if (ouraddress == address) {
					slf4jLogger.info("==============EV3 received money!!!!==================");
					slf4jLogger.info(" Output TO OUR address !!! from: " + address.toString() +
							" amount " + output.getValue());
					slf4jLogger.info("================================");

				} else {
					// slf4jLogger.info("our address: " + ouraddress + " TO address: " + address);
				}
			} catch (Exception epicfail) {
				slf4jLogger.error("Uhoh address check stuff failed: " + epicfail.getMessage(), epicfail);
			}
		}
	}
}
