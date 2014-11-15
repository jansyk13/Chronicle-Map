/*
 * Copyright 2014 Higher Frequency Trading
 *
 * http://www.higherfrequencytrading.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.chronicle.map.fromdocs;

import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.map.OffHeapUpdatableChronicleMapBuilder;
import net.openhft.chronicle.map.ReadContext;
import net.openhft.chronicle.map.WriteContext;
import net.openhft.lang.model.DataValueClasses;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import static org.junit.Assert.assertEquals;

/**
 * These code fragments will appear in an article on OpenHFT. These tests to ensure that the examples compile
 * and behave as expected.
 */
public class OpenJDKAndHashMapExamplesTest {
    private static final SimpleDateFormat YYYYMMDD = new SimpleDateFormat("yyyyMMdd");


    private static final String TMP = System.getProperty("java.io.tmpdir");

    private static long parseYYYYMMDD(String s) {
        try {
            return YYYYMMDD.parse(s).getTime();
        } catch (ParseException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void bondExample() throws IOException, InterruptedException {

        File file = new File(TMP + "/chm-myBondPortfolioCHM-" + System.nanoTime());
        file.deleteOnExit();

        ChronicleMap<String, BondVOInterface> chm = OffHeapUpdatableChronicleMapBuilder
                .of(String.class, BondVOInterface.class)
                .keySize(10)
                .createPersistedTo(file);

        BondVOInterface bondVO = DataValueClasses.newDirectReference(BondVOInterface.class);
        try (WriteContext wc = chm.acquireUsingLocked("369604103", bondVO)) {
            bondVO.setIssueDate(parseYYYYMMDD("20130915"));
            bondVO.setMaturityDate(parseYYYYMMDD("20140915"));
            bondVO.setCoupon(5.0 / 100); // 5.0%

            BondVOInterface.MarketPx mpx930 = bondVO.getMarketPxIntraDayHistoryAt(0);
            mpx930.setAskPx(109.2);
            mpx930.setBidPx(106.9);

            BondVOInterface.MarketPx mpx1030 = bondVO.getMarketPxIntraDayHistoryAt(1);
            mpx1030.setAskPx(109.7);
            mpx1030.setBidPx(107.6);
        }

        ChronicleMap<String, BondVOInterface> chmB = OffHeapUpdatableChronicleMapBuilder
                .of(String.class, BondVOInterface.class)
                .keySize(10)
                .createPersistedTo(file);

        // ZERO Copy but creates a new off heap reference each time
        // our reusable, mutable off heap reference, generated from the interface.
        BondVOInterface bond = DataValueClasses.newDirectReference(BondVOInterface.class);
        try (ReadContext rc = chmB.getUsingLocked("369604103", bond)) {
            if (rc.present()) {
                assertEquals(5.0 / 100, bond.getCoupon(), 0.0);

                BondVOInterface.MarketPx mpx930B = bond.getMarketPxIntraDayHistoryAt(0);
                assertEquals(109.2, mpx930B.getAskPx(), 0.0);
                assertEquals(106.9, mpx930B.getBidPx(), 0.0);

                BondVOInterface.MarketPx mpx1030B = bond.getMarketPxIntraDayHistoryAt(1);
                assertEquals(109.7, mpx1030B.getAskPx(), 0.0);
                assertEquals(107.6, mpx1030B.getBidPx(), 0.0);
            }
        }

        // lookup the key and give me a reference I can update in a thread safe way.
        try (WriteContext wc = chm.acquireUsingLocked("369604103", bond)) {
            // found a key and bond has been set
            // get directly without touching the rest of the record.
            long _matDate = bond.getMaturityDate();
            // write just this field, again we need to assume we are the only writer.
            bond.setMaturityDate(parseYYYYMMDD("20440315"));

            //demo of how to do OpenHFT off-heap array[ ] processing
            int tradingHour = 2;  //current trading hour intra-day
            BondVOInterface.MarketPx mktPx = bond.getMarketPxIntraDayHistoryAt(tradingHour);
            if (mktPx.getCallPx() < 103.50) {
                mktPx.setParPx(100.50);
                mktPx.setAskPx(102.00);
                mktPx.setBidPx(99.00);
                // setMarketPxIntraDayHistoryAt is not needed as we are using zero copy,
                // the original has been changed.
            }
        }

        // bond will be full of default values and zero length string the first time.

        // from this point, all operations are completely record/entry local,
        // no other resource is involved.
        // now perform thread safe operations on my reference
        bond.addAtomicMaturityDate(16 * 24 * 3600 * 1000L);  //20440331


        bond.addAtomicCoupon(-1 * bond.getCoupon()); //MT-safe! now a Zero Coupon Bond.

        // cleanup.
        chm.close();
        chmB.close();
        file.delete();
    }
}