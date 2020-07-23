/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2000, 2014 Oracle and/or its affiliates.  All rights reserved.
 *
 */
package com.sleepycat.collections.test.serial;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.sleepycat.bind.serial.SerialBinding;
import com.sleepycat.bind.serial.StoredClassCatalog;
import com.sleepycat.collections.StoredMap;
import com.sleepycat.collections.TransactionRunner;
import com.sleepycat.collections.TransactionWorker;
import com.sleepycat.compat.DbCompat;
import com.sleepycat.db.Database;
import com.sleepycat.db.DatabaseConfig;
import com.sleepycat.db.Environment;
import com.sleepycat.util.test.SharedTestUtils;
import com.sleepycat.util.test.TestBase;
import com.sleepycat.util.test.TestEnv;

/**
 * Runs part one of the StoredClassCatalogTest.  This part is run with the
 * old/original version of TestSerial in the classpath.  It creates a fresh
 * environment and databases containing serialized versions of the old class.
 * When StoredClassCatalogTest is run, it will read these objects from the
 * database created here.
 *
 * @author Mark Hayes
 */
@RunWith(Parameterized.class)
public class StoredClassCatalogTestInit extends TestBase
    implements TransactionWorker {

    static final String CATALOG_FILE = StoredClassCatalogTest.CATALOG_FILE;
    static final String STORE_FILE = StoredClassCatalogTest.STORE_FILE;

    @Parameters
    public static List<Object[]> genParams() {
        List<Object[]> params = new ArrayList<Object[]>();
        for (TestEnv testEnv : TestEnv.ALL)
            params.add(new Object[]{testEnv});
        
        return params;
    }

    private TestEnv testEnv;
    private Environment env;
    private StoredClassCatalog catalog;
    private Database store;
    private Map map;
    private TransactionRunner runner;

    public StoredClassCatalogTestInit(TestEnv testEnv) {

        this.testEnv = testEnv;
        customName = StoredClassCatalogTest.makeTestName(testEnv);
    }

    @Before
    public void setUp()
        throws Exception {
        
        SharedTestUtils.printTestName(customName);
        env = testEnv.open(customName);
        runner = new TransactionRunner(env);

        catalog = new StoredClassCatalog(openDb(CATALOG_FILE));

        SerialBinding keyBinding = new SerialBinding(catalog, String.class);
        SerialBinding valueBinding =
            new SerialBinding(catalog, TestSerial.class);
        store = openDb(STORE_FILE);

        map = new StoredMap(store, keyBinding, valueBinding, true);
    }

    private Database openDb(String file)
        throws Exception {

        DatabaseConfig config = new DatabaseConfig();
        DbCompat.setTypeBtree(config);
        config.setTransactional(testEnv.isTxnMode());
        config.setAllowCreate(true);

        return DbCompat.testOpenDatabase(env, null, file, null, config);
    }

    @After
    public void tearDown() 
        throws Exception {

        try {
            if (catalog != null) {
                catalog.close();
                catalog.close(); // should have no effect
            }
            if (store != null) {
                store.close();
            }
            if (env != null) {
                env.close();
            }
            
            /* 
             * Copy environment generated by this test to test dest dir.
             * Since the environment is necessary for StoreClassCatalogTest.
             */
            SharedTestUtils.copyDir(testEnv.getDirectory(customName, false), 
                new File(SharedTestUtils.getDestDir(), customName));
            
        } catch (Exception e) {
            System.err.println("Ignored exception during tearDown: ");
            e.printStackTrace();
        } finally {
            /* Ensure that GC can cleanup. */
            catalog = null;
            store = null;
            env = null;
            testEnv = null;
            map = null;
            runner = null;
        }
    }

    @Test
    public void runTest()
        throws Exception {
        
        runner.run(this);
    }
    
    public void doWork() {
        TestSerial one = new TestSerial(null);
        TestSerial two = new TestSerial(one);
        assertNull("Likely the classpath contains the wrong version of the" +
                   " TestSerial class, the 'original' version is required",
                   one.getStringField());
        assertNull(two.getStringField());
        map.put("one", one);
        map.put("two", two);
        one = (TestSerial) map.get("one");
        two = (TestSerial) map.get("two");
        assertEquals(one, two.getOther());
        assertNull(one.getStringField());
        assertNull(two.getStringField());
    }
}
