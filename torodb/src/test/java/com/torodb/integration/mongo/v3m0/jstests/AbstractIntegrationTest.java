/*
 *     This file is part of ToroDB.
 *
 *     ToroDB is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     ToroDB is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with ToroDB. If not, see <http://www.gnu.org/licenses/>.
 *
 *     Copyright (c) 2014, 8Kdata Technology
 *     
 */

package com.torodb.integration.mongo.v3m0.jstests;

import com.google.common.base.Charsets;
import com.google.common.collect.Multimap;
import com.torodb.config.model.Config;
import com.torodb.integration.IntegrationTestEnvironment;
import com.torodb.integration.TestCategory;
import com.torodb.integration.ToroRunnerClassRule;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import org.junit.AssumptionViolatedException;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameter;
import org.slf4j.Logger;

public abstract class AbstractIntegrationTest {

	@ClassRule
	public final static ToroRunnerClassRule TORO_RUNNER_CLASS_RULE = new ToroRunnerClassRule();

    private final Logger logger;

    @Parameter(0)
    public TestCategory category;
    @Parameter(1)
    public Script script;

    public AbstractIntegrationTest(Logger logger) {
        this.logger = logger;
    }

    @Test
    @SuppressWarnings("fallthrough")
	public void runJstest() throws Exception {
        boolean expectedZeroResult;
        boolean exceptionsExpected;
        switch (category) {
            case FAILING:
                expectedZeroResult = false;
                exceptionsExpected = false;
                break;
            case WORKING:
                expectedZeroResult = true;
                exceptionsExpected = false;
                break;
            case CATASTROPHIC:
                logger.warn("Ignoring the known problematic script: {}", script);
            default:
                throw new AssumptionViolatedException("Test " + script + " is ignored because its category is " + category);
        }

		Config config = TORO_RUNNER_CLASS_RULE.getConfig();

		String toroConnectionString = config.getProtocol().getMongo().getNet().getBindIp() + ":"
				+ config.getProtocol().getMongo().getNet().getPort() + "/"
				+ config.getBackend().asPostgres().getDatabase();
		URL mongoMocksUrl = AbstractIntegrationTest.class.getResource("mongo_mocks.js");

        logger.info("Testing {}", script);

        Process mongoProcess = Runtime.getRuntime()
				.exec(new String[] {
					"mongo",
					toroConnectionString, 
					mongoMocksUrl.getPath(),
					script.getURL().getPath(),
				});
		InputStream inputStream = mongoProcess.getInputStream();
		InputStream erroStream = mongoProcess.getErrorStream();
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

		int result = mongoProcess.waitFor();
		
		List<Throwable> uncaughtExceptions = TORO_RUNNER_CLASS_RULE.getUcaughtExceptions();
		
		if (result != 0) {
			int read;
			
			while ((read = inputStream.read()) != -1) {
				byteArrayOutputStream.write(read);
			}

			while ((read = erroStream.read()) != -1) {
				byteArrayOutputStream.write(read);
			}
		}
		
		if (!uncaughtExceptions.isEmpty()) {
			PrintStream printStream = new PrintStream(byteArrayOutputStream, true, Charsets.UTF_8.name());
			
			for (Throwable throwable : uncaughtExceptions) {
				throwable.printStackTrace(printStream);
			}
		}

        if (expectedZeroResult) {
            if (result != 0) {
                String reason = new String(byteArrayOutputStream.toByteArray(), Charsets.UTF_8);
                throw new AssertionError("Test " + script + " failed:\n" + reason);
            }
        }
        else {
            if (result == 0) {
                throw new AssertionError("Test " + script + " should fail, but it didn't");
            }
        }

        if (!exceptionsExpected && !uncaughtExceptions.isEmpty()) {
            String exceptions = new String(byteArrayOutputStream.toByteArray(), Charsets.UTF_8);
            throw new AssertionError("Test " + script + " did not failed but "
                    + "following exception where received:\n" + exceptions);
        }
	}

    protected static Collection<Object[]> parameters(ScriptClassifier classifier) {
        IntegrationTestEnvironment ite = IntegrationTestEnvironment.CURRENT_INTEGRATION_TEST_ENVIRONMENT;

        Multimap<TestCategory, Script> scriptFor = classifier.getScriptFor(ite);

        Collection<Object[]> result = new ArrayList<>(scriptFor.size());

        for (Entry<TestCategory, Script> entry : scriptFor.entries()) {
            result.add(new Object[] {entry.getKey(), entry.getValue()});
        }
        
        return result;
    }
    
}