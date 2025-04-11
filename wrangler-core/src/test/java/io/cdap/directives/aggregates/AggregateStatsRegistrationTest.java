/*
 * Copyright © 2023 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.cdap.directives.aggregates;

import io.cdap.wrangler.registry.DirectiveInfo;
import io.cdap.wrangler.registry.SystemDirectiveRegistry;
import org.junit.Assert;
import org.junit.Test;

/**
 * A test class to verify AggregateStats directive is properly registered in the system.
 */
public class AggregateStatsRegistrationTest {

  @Test
  public void testAggregateStatsDirectiveRegistration() throws Exception {
    // Get the singleton instance of the registry
    SystemDirectiveRegistry registry = SystemDirectiveRegistry.INSTANCE;
    
    // Check if the directive exists
    DirectiveInfo info = registry.get("aggregate-stats");
    
    // Verify directive is registered
    Assert.assertNotNull("aggregate-stats directive should be registered", info);
    
    // Verify it's the correct class
    Assert.assertEquals(
      "Registered class should be AggregateStats",
      AggregateStats.class.getName(),
      info.instance().getClass().getName()
    );
    
    // Verify the directive name
    Assert.assertEquals(
      "Directive name should be 'aggregate-stats'",
      "aggregate-stats", 
      info.name()
    );
  }
} 