package org.xel.execution;

import org.xel.*;
import org.xel.computation.CommandCancelWork;
import org.xel.computation.CommandNewWork;
import org.xel.computation.CommandPowBty;
import org.xel.computation.MessageEncoder;
import org.xel.crypto.Crypto;
import org.xel.db.DbIterator;
import org.xel.helpers.RedeemFunctions;
import org.xel.util.Convert;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.xel.util.Logger;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Properties;

import static org.xel.helpers.FileReader.readFile;

/******************************************************************************
 * Copyright © 2017 The XEL Core Developers.                                  *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * XEL software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

public class WorkTest extends AbstractForgingTest {

    protected static boolean isNxtInitted = false;

    @Before
    public void init() {
        if(!isNxtInitted && !Nxt.isInitialized()) {
            Properties properties = AbstractForgingTest.newTestProperties();
            properties.setProperty("nxt.disableGenerateBlocksThread", "false");
            properties.setProperty("nxt.enableFakeForging", "true");
            AbstractForgingTest.init(properties);
            Assert.assertTrue("nxt.fakeForgingAccount must be defined in nxt.properties", Nxt.getStringProperty("nxt.fakeForgingAccount") != null);
            isNxtInitted = true;
        }
    }

    @After
    public void destroy() {
        AbstractForgingTest.shutdown();
    }

    public void redeemPubkeyhash(){
        Nxt.getTemporaryComputationBlockchainProcessor().popOffTo(0);

        String address = "1XELjH6JgPS48ZL7ew1Zz2xxczyzqit3h";
        String[] privkeys = new String[]{"5JDSuYmvAAF85XFQxPTkHGFrNfAk3mhtZKmXvsLJiFZ7tDrSBmp"};
        Assert.assertTrue("Failed to create redeem transaction.", RedeemFunctions.redeem(address, AbstractForgingTest.testForgingSecretPhrase, privkeys));
    }

    @Test
    public void newWorkTest() throws NxtException, IOException {

        redeemPubkeyhash();

        String code = readFile("src/test/testfiles/op2.epl", Charset.forName("UTF-8"));
        String doublecheckcode = new String(code.getBytes());
        System.out.println("[!!]\tcode length: " + code.length());
        CommandNewWork work = new CommandNewWork(100, (short)15,1000001,1000001,10,10, code.getBytes());
        MessageEncoder.push(work, AbstractForgingTest.testForgingSecretPhrase, 5);

        // Mine a bit so the work gets confirmed
        AbstractBlockchainTest.forgeNumberOfBlocks(1, AbstractForgingTest.testForgingSecretPhrase);

        // Test work db table
        Assert.assertEquals(1, Work.getCount());
        Assert.assertEquals(1, Work.getActiveCount());
        long id = 0;
        try(DbIterator<Work> wxx = Work.getActiveWork()){
            Work w = wxx.next();
            id = w.getId();
            System.out.println("Found work in DB with id = " + Long.toUnsignedString(w.getId()));
        }

        CommandCancelWork cancel = new CommandCancelWork(id);
        MessageEncoder.push(cancel, AbstractForgingTest.testForgingSecretPhrase, 5);

        // Mine a bit so the work gets confirmed
        AbstractBlockchainTest.forgeNumberOfBlocks(5, AbstractForgingTest.testForgingSecretPhrase);

        System.out.println("LAST BLOCK:");
        System.out.println(Nxt.getTemporaryComputationBlockchain().getLastBlock().getJSONObject().toJSONString());

        // Test work db table
        Assert.assertEquals(1, Work.getCount());
        Assert.assertEquals(0, Work.getActiveCount());


    }

    @Test
    public void newWorkTestWithNaturalTimeout() throws NxtException, IOException {

        redeemPubkeyhash();
        String code = readFile("src/test/testfiles/btc.epl", Charset.forName("UTF-8"));
        System.out.println("[!!]\tcode length: " + code.length());
        CommandNewWork work = new CommandNewWork(100, (short)15,1000001,1000001,10,10, code.getBytes());
        MessageEncoder.push(work, AbstractForgingTest.testForgingSecretPhrase, 5);

        // Mine a bit so the work gets confirmed
        AbstractBlockchainTest.forgeNumberOfBlocks(1, AbstractForgingTest.testForgingSecretPhrase);

        // Test work db table
        Assert.assertEquals(1, Work.getCount());
        Assert.assertEquals(1, Work.getActiveCount());

        // Mine a bit so the work times out
        AbstractBlockchainTest.forgeNumberOfBlocks(20, AbstractForgingTest.testForgingSecretPhrase);

        // Test work db table
        Assert.assertEquals(1, Work.getCount());
        Assert.assertEquals(0, Work.getActiveCount());

    }

    @Test
    public void newWorkTestWithEnoughPOWs() throws NxtException, IOException {

        redeemPubkeyhash();
        String code = readFile("src/test/testfiles/btc.epl", Charset.forName("UTF-8"));
        CommandNewWork work = new CommandNewWork(10, (short)100,1000001,1000001,10,10, code.getBytes());
        MessageEncoder.push(work, AbstractForgingTest.testForgingSecretPhrase,5);

        // Mine a bit so the work gets confirmed
        AbstractBlockchainTest.forgeNumberOfBlocks(1, AbstractForgingTest.testForgingSecretPhrase);

        long id = 0;
        Work w;
        try(DbIterator<Work> wxx = Work.getActiveWork()){
            w = wxx.next();
            id = w.getId();
        }


        // Test work db table
        Assert.assertEquals(1, Work.getCount());
        Assert.assertEquals(1, Work.getActiveCount());
        byte[] m = new byte[32];
        byte[] m2 = new byte[32];

        byte[] testarray = new byte[32*4];
        for(int i=0;i<25; ++i) {
            m[0]=(byte)(m[0]+1);
            CommandPowBty pow = new CommandPowBty(id, true, m, new byte[16], testarray, 0, w.getCurrentRound(), Crypto.getPublicKey(AbstractForgingTest.testForgingSecretPhrase));
            m2[0]=(byte)(m2[0]+2);
            m2[1]=1;
            CommandPowBty pow2 = new CommandPowBty(id, true, m2, new byte[16], testarray, 0, w.getCurrentRound(),  Crypto.getPublicKey(AbstractForgingTest.testForgingSecretPhrase));

            try {
                MessageEncoder.push(pow, AbstractForgingTest.testForgingSecretPhrase, 1);
            }catch(Exception e){
                Logger.logDebugMessage("Could not push POW: " + e.getMessage());
            }
            try {
                MessageEncoder.push(pow2, AbstractForgingTest.testForgingSecretPhrase, 1);
            }catch(Exception e){
                Logger.logDebugMessage("Could not push POW: " + e.getMessage());
            }

            // Mine a bit so the work times out
            AbstractBlockchainTest.forgeNumberOfBlocks(1, AbstractForgingTest.testForgingSecretPhrase);
        }
        AbstractBlockchainTest.forgeNumberOfBlocks(6, AbstractForgingTest.testForgingSecretPhrase);

        // After getting enough Pow work must be closed
        // Test work db table
        Assert.assertEquals(1, Work.getCount());
        Assert.assertEquals(0, Work.getActiveCount());
    }

    /*


    @Test
    public void newWorkTestWithEnoughPOWs() throws NxtException, IOException {

        redeemPubkeyhash();
        String code = readFile("test/testfiles/btc.epl", Charset.forName("UTF-8"));
        System.out.println("[!!]\tcode length: " + code.length());
        CommandNewWork work = new CommandNewWork(10, (short)100,1000001,1000001,10,10, code.getBytes());
        MessageEncoder.push(work, AbstractForgingTest.testForgingSecretPhrase);

        // Mine a bit so the work gets confirmed
        AbstractBlockchainTest.forgeNumberOfBlocks(1, AbstractForgingTest.testForgingSecretPhrase);

        long id = 0;
        try(DbIterator<Work> wxx = Work.getActiveWork()){
            Work w = wxx.next();
            id = w.getId();
            System.out.println("Found work in DB with id = " + Long.toUnsignedString(w.getId()));
        }

        long lastId = 0;

        // Test work db table
        Assert.assertEquals(1, Work.getCount());
        Assert.assertEquals(1, Work.getActiveCount());
        byte[] m = new byte[32];
        byte[] testarray = new byte[0];
        for(int i=0;i<25; ++i) {
            m[0]=(byte)(m[0]+1);
            CommandPowBty pow = new CommandPowBty(id, lastId, true, m, new byte[32], testarray, 0);
            lastId = MessageEncoder.push(pow, AbstractForgingTest.testForgingSecretPhrase);
            // Mine a bit so the work times out
            AbstractBlockchainTest.forgeNumberOfBlocks(1, AbstractForgingTest.testForgingSecretPhrase);
        }
        AbstractBlockchainTest.forgeNumberOfBlocks(6, AbstractForgingTest.testForgingSecretPhrase);

        // After getting enough Pow work must be closed
        // Test work db table
        Assert.assertEquals(1, Work.getCount());
        Assert.assertEquals(0, Work.getActiveCount());
    }

    @Test
    public void newWorkTestWithBounties() throws NxtException, IOException {

        redeemPubkeyhash();
        String code = readFile("test/testfiles/bountytest.epl", Charset.forName("UTF-8"));
        System.out.println("[!!]\tcode length: " + code.length());
        CommandNewWork work = new CommandNewWork(10, (short)100,1000001,1000001,2,2, code.getBytes());
        MessageEncoder.push(work, AbstractForgingTest.testForgingSecretPhrase);
        Work w = null;
        // Mine a bit so the work gets confirmed
        AbstractBlockchainTest.forgeNumberOfBlocks(1, AbstractForgingTest.testForgingSecretPhrase);

        long id = 0;
        try(DbIterator<Work> wxx = Work.getActiveWork()){
            w = wxx.next();
            id = w.getId();
            System.out.println("Found work in DB with id = " + Long.toUnsignedString(w.getId()) + ", source code len = " + w.getSource_code().length() + " (" + w.getSource_code().substring(0,25) + "...)");
        }

        Assert.assertEquals(0, Work.getWorkById(id).getReceived_bounties()); // Did the bounty count correctly???


        // Test work db table
        Assert.assertEquals(1, Work.getCount());
        Assert.assertEquals(1, Work.getActiveCount());
        long lastId = 0;

        {
            int[] m = new int[8];
            int[] testarray = new int[w.getStorage_size()];
            testarray[0] = 6000;
            CommandPowBty pow = new CommandPowBty(id, 0 , false, Convert.int2byte(m), new byte[32],
            Convert
                    .int2byte(testarray), 0);
            lastId = MessageEncoder.push(pow, AbstractForgingTest.testForgingSecretPhrase);
            AbstractBlockchainTest.forgeNumberOfBlocks(5, AbstractForgingTest.testForgingSecretPhrase);
        }


        // After getting enough Pow work must be closed
        // Test work db table
        System.out.println(Work.getWorkById(id).getVerifyFunction());
        Assert.assertEquals(1, Work.getCount());
        Assert.assertEquals(1, Work.getActiveCount());
        Assert.assertEquals(1, Work.getWorkById(id).getReceived_bounties()); // Did the bounty count correctly???

        {
            int[] m = new int[8];
            m[0]=1;
            int[] testarray = new int[w.getStorage_size()];
            testarray[0] = 6000;
            CommandPowBty pow = new CommandPowBty(id, 0 , false, Convert.int2byte(m), new byte[32],  Convert
            .int2byte(testarray), 0);
            MessageEncoder.push(pow, AbstractForgingTest.testForgingSecretPhrase);
            AbstractBlockchainTest.forgeNumberOfBlocks(5, AbstractForgingTest.testForgingSecretPhrase);
        }

        Assert.assertEquals(2, Work.getWorkById(id).getReceived_bounties()); // Last one didnt work, still only got 1 valid bty

        {
            int[] m = new int[8];
            m[0]=194;
            int[] testarray = new int[w.getStorage_size()];
            testarray[0] = 3000;
            CommandPowBty pow = new CommandPowBty(id, 0 , false, Convert.int2byte(m), new byte[32], Convert
            .int2byte(testarray), 0);
            MessageEncoder.push(pow, AbstractForgingTest.testForgingSecretPhrase);
            AbstractBlockchainTest.forgeNumberOfBlocks(5, AbstractForgingTest.testForgingSecretPhrase);
        }

        // Work still open
        Assert.assertEquals(1, Work.getActiveCount());

        Assert.assertEquals(3, Work.getWorkById(id).getReceived_bounties()); // This one must have worked

        {
            int[] m = new int[8];
            m[0]=19435;
            int[] testarray = new int[w.getStorage_size()];
            testarray[0] = 26000;
            CommandPowBty pow = new CommandPowBty(id, 0 , false, Convert.int2byte(m), new byte[32], Convert
            .int2byte(testarray), 0);
            MessageEncoder.push(pow, AbstractForgingTest.testForgingSecretPhrase);
        }
        {
            int[] m = new int[8];
            m[0]=17;
            int[] testarray = new int[w.getStorage_size()];
            testarray[0] = 46000;
            CommandPowBty pow = new CommandPowBty(id, 0 , false, Convert.int2byte(m), new byte[32], Convert
            .int2byte(testarray), 0);
            MessageEncoder.push(pow, AbstractForgingTest.testForgingSecretPhrase);
        }

        // Also do some other good ones in the same block (see if the cut off mechanism works)
        {
            int[] m = new int[8];
            m[0]=44;
            int[] testarray = new int[w.getStorage_size()];
            testarray[0] = 76003;
            CommandPowBty pow = new CommandPowBty(id, 0 , false, Convert.int2byte(m), new byte[32], Convert.int2byte(testarray), 0);
            MessageEncoder.push(pow, AbstractForgingTest.testForgingSecretPhrase);
        }

        // Forge two at the same time, should work as well! Check the order carefully during testing!
        AbstractBlockchainTest.forgeNumberOfBlocks(5, AbstractForgingTest.testForgingSecretPhrase);

        // ATTENTION: 4, because after 4 btys the job is closed, the 5th one is just ignored!
        Assert.assertEquals(4, Work.getWorkById(id).getReceived_bounties()); // This one must have worked
        Assert.assertEquals(0, Work.getActiveCount());



    } */
}
