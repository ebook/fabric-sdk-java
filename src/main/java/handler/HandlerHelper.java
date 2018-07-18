package handler;

import opensource.SampleOrg;
import opensource.SampleStore;
import opensource.SampleUser;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.CryptoException;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * 
 */
public class HandlerHelper {
    private static InitConfig initConfig =null;
    //private static InitConfig initConfig = InitConfig.getConfig("/src/main/resources/fixture/config/hdrNoDelete-sdk-config.sm.yaml");
    //private static TestConfig testConfig = TestConfig.getConfig();
    private static String TEST_ADMIN_NAME = "admin";
    private static String TESTUSER_1_NAME = "user1";

    private static String CHAIN_CODE_NAME = "example02";
    private static String CHAIN_CODE_VERSION = "1.0";
    private static String CHANNEL_NAME = "example";

    private static String INVOKE_FINCTION_NAME = "invoke";
    private static String INVOKE_QUERY_NAME = "query";

    private static String orgName = "266d0f487933503a48f0ab728b85d5b469cb2b79";

    private HFClient client = null;
    private Channel channel = null;
    private ChaincodeID chaincodeID = null;


    private CryptoSuite initCryptoSuite(String type) throws IllegalAccessException, InvocationTargetException, InvalidArgumentException, InstantiationException, NoSuchMethodException, CryptoException, ClassNotFoundException {
        CryptoSuite cs = null;
//        if (type.equals("SW") || type.equals("sw") ||type.equals("")){
//            initConfig = InitConfig.getConfig("/src/main/resources/fixture/config/hdrNoDelete-sdk-config.yaml");
//            cs= CryptoSuite.Factory.getCryptoSuite();
//          }else if (type.equals("sm") || type.equals("SM")){
//            initConfig = InitConfig.getConfig("/src/main/resources/fixture/config/hdrNoDelete-sdk-config.sm.yaml");
//            cs = CryptoSuite.Factory.getCryptoSuite(initConfig.getSMProperties());
//        }
//        initConfig = InitConfig.getConfig("/src/main/resources/fixture/config/javaSDKVerify-sdk-config.yaml");
        initConfig = InitConfig.getConfig("/src/main/resources/bcs-itetze-sdk-config.yaml");
        cs= CryptoSuite.Factory.getCryptoSuite();

        return cs;
    }

    public void init() {
        //Create instance of client.
        client = HFClient.createNewInstance();
        try {
           // client.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite(initConfig.getSMProperties()));
            CryptoSuite cs = initCryptoSuite("sw");
            client.setCryptoSuite(cs);
        } catch (Exception e) {
            e.printStackTrace();
        }
        //Set up USERS
        File sampleStoreFile = new File(System.getProperty("java.io.tmpdir") + "/HFCSampletest.properties");
        if (sampleStoreFile.exists()) { //For testing start fresh
            sampleStoreFile.delete();
        }
        final SampleStore sampleStore = new SampleStore(sampleStoreFile);
        // get users for all orgs
        Collection<SampleOrg> testSampleOrgs = initConfig.getIntegrationSampleOrgs();
        for (SampleOrg sampleOrg : testSampleOrgs) {
            final String orgName = sampleOrg.getName();
            SampleUser admin = sampleStore.getMember(TEST_ADMIN_NAME, orgName);
            sampleOrg.setAdmin(admin); // The admin of this org.
            // No need to enroll or register all done in End2endIt !
            SampleUser user = sampleStore.getMember(TESTUSER_1_NAME, orgName);
            sampleOrg.addUser(user);  //Remember user belongs to this Org

            final String sampleOrgName = sampleOrg.getName();
            try {
                SampleUser peerOrgAdmin = sampleStore.getMember(sampleOrgName + "Admin", sampleOrgName, sampleOrg.getMSPID(),
                        findFileSk(Paths.get(sampleOrg.getKeystorePath()).toFile()),
                        Paths.get(sampleOrg.getSigncertsPath()).toFile());
                sampleOrg.setPeerAdmin(peerOrgAdmin); //A special user that can create channels, join peers and install chaincode
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        chaincodeID = ChaincodeID.newBuilder().setName(CHAIN_CODE_NAME).setVersion(CHAIN_CODE_VERSION).build();
    }

    public void newChannel() throws Exception {
        SampleOrg sampleOrg = initConfig.getIntegrationSampleOrg(orgName);
        client.setUserContext(sampleOrg.getPeerAdmin());

        channel = client.newChannel(CHANNEL_NAME);
        for (String orderName : sampleOrg.getOrdererNames()) {
            channel.addOrderer(client.newOrderer(orderName, sampleOrg.getOrdererLocation(orderName),
                    sampleOrg.getOrdererProperties(orderName)));
        }

        for (String peerName : sampleOrg.getPeerNames()) {
            String peerLocation = sampleOrg.getPeerLocation(peerName);
            Peer peer = client.newPeer(peerName, peerLocation, sampleOrg.getPeerProperties(peerName));

            //Query the actual peer for which channels it belongs to and check it belongs to this channel
            Set<String> channels = client.queryChannels(peer);
            if (!channels.contains(CHANNEL_NAME)) {
                throw new AssertionError(format("Peer %s does not appear to belong to channel %s", peerName, CHANNEL_NAME));
            }

            channel.addPeer(peer);
            sampleOrg.addPeer(peer);
        }

        for (String eventHubName : sampleOrg.getEventHubNames()) {
            EventHub eventHub = client.newEventHub(eventHubName, sampleOrg.getEventHubLocation(eventHubName),
                    sampleOrg.getPeerProperties(eventHubName));
            channel.addEventHub(eventHub);
        }

        channel.initialize();
        //channel.setTransactionWaitTime(testConfig.getTransactionWaitTime());
        //channel.setDeployWaitTime(testConfig.getDeployWaitTime());
    }

    public boolean invoke(String[] args) {
        Collection<ProposalResponse> successful = new LinkedList<>();
        Collection<ProposalResponse> failed = new LinkedList<>();
        try {
            /// Send transaction proposal to all peers
            TransactionProposalRequest transactionProposalRequest = client.newTransactionProposalRequest();
            transactionProposalRequest.setChaincodeID(chaincodeID);
            transactionProposalRequest.setFcn(INVOKE_FINCTION_NAME);
            //transactionProposalRequest.setProposalWaitTime(testConfig.getProposalWaitTime());
            transactionProposalRequest.setArgs(args);

            Map<String, byte[]> tm2 = new HashMap<>();
            tm2.put("HyperLedgerFabric", "TransactionProposalRequest:JavaSDK".getBytes(UTF_8));
            tm2.put("method", "TransactionProposalRequest".getBytes(UTF_8));
            tm2.put("result", ":)".getBytes(UTF_8));  /// This should be returned see chaincode.
            try {
                transactionProposalRequest.setTransientMap(tm2);
            } catch (Exception e) {
            }

            Collection<ProposalResponse> transactionPropResp = channel.sendTransactionProposal(transactionProposalRequest, channel.getPeers());
            for (ProposalResponse response : transactionPropResp) {
                if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                    // out("Successful transaction proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName());
                    successful.add(response);
                } else {
                    failed.add(response);
                }
            }

            // Check that all the proposals are consistent with each other. We should have only one set
            // where all the proposals above are consistent.
            Collection<Set<ProposalResponse>> proposalConsistencySets = SDKUtils.getProposalConsistencySets(transactionPropResp);
            if (proposalConsistencySets.size() != 1) {
                out(format("Expected only one set of consistent proposal responses but got %d", proposalConsistencySets.size()));
            }

            //out("Received %d transaction proposal responses. Successful+verified: %d . Failed: %d",
            //        transactionPropResp.size(), successful.size(), failed.size());
            if (failed.size() > 0) {
                ProposalResponse firstTransactionProposalResponse = failed.iterator().next();
                out("Invoke:" + failed.size() + " endorser error: " +
                        firstTransactionProposalResponse.getMessage() +
                        ". Was verified: " + firstTransactionProposalResponse.isVerified());
            }
            //out("Successfully received transaction proposal responses.");

            // Send Transaction Transaction to orderer
            //BlockEvent.TransactionEvent transactionEvent = channel.sendTransaction(successful).get(testConfig.getTransactionWaitTime(), TimeUnit.SECONDS);
            BlockEvent.TransactionEvent transactionEvent = channel.sendTransaction(successful).get(initConfig.getWaiteTime(), TimeUnit.SECONDS);

            if (transactionEvent.isValid()) {
                //ut("Finished transaction with transaction id %s", transactionEvent.getTransactionID());
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            //out("Caught an exception while invoking chaincode");
            e.printStackTrace();
            return false;
            //fail("Failed invoking chaincode with error : " + e.getMessage());
        }
    }

    public String query(String[] args) {
        try {
            //out("Now query chaincode for the value of b.");
            QueryByChaincodeRequest queryByChaincodeRequest = client.newQueryProposalRequest();
            queryByChaincodeRequest.setArgs(args);
            queryByChaincodeRequest.setFcn(INVOKE_QUERY_NAME);
            queryByChaincodeRequest.setChaincodeID(chaincodeID);

            Map<String, byte[]> tm2 = new HashMap<>();
            tm2.put("HyperLedgerFabric", "QueryByChaincodeRequest:JavaSDK".getBytes(UTF_8));
            tm2.put("method", "QueryByChaincodeRequest".getBytes(UTF_8));
            queryByChaincodeRequest.setTransientMap(tm2);

            Collection<ProposalResponse> queryProposals = channel.queryByChaincode(queryByChaincodeRequest, channel.getPeers());
            for (ProposalResponse proposalResponse : queryProposals) {
                if (!proposalResponse.isVerified() || proposalResponse.getStatus() != ProposalResponse.Status.SUCCESS) {
                    out("Failed query proposal from peer " + proposalResponse.getPeer().getName() + " status: " + proposalResponse.getStatus() +
                            ". Messages: " + proposalResponse.getMessage()
                            + ". Was verified : " + proposalResponse.isVerified());
                } else {
                    String payload = proposalResponse.getProposalResponse().getResponse().getPayload().toStringUtf8();
                    out("Query payload from peer %s returned %s", proposalResponse.getPeer().getName(), payload);
                    return payload;
                }
            }
        } catch (Exception e) {
            out("Caught exception while running query");
            e.printStackTrace();
            out("Failed during chaincode query with error : " + e.getMessage());
        }
        return null;
    }

    public void close() {
        channel.shutdown(true);
    }

    static void out(String format, Object... args) {
        System.err.flush();
        System.out.flush();

        System.out.println(format(format, args));
        System.err.flush();
        System.out.flush();

    }

    private static File findFileSk(File directory) {

        File[] matches = directory.listFiles((dir, name) -> name.endsWith("_sk"));

        if (null == matches) {
            throw new RuntimeException(format("Matches returned null does %s directory exist?", directory.getAbsoluteFile().getName()));
        }

        if (matches.length != 1) {
            throw new RuntimeException(format("Expected in %s only 1 sk file but found %d", directory.getAbsoluteFile().getName(), matches.length));
        }

        return matches[0];

    }
}
