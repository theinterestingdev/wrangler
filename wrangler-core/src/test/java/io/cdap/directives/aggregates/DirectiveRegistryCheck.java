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

/**
 * A simple utility class to check if a directive is properly registered in the system.
 */
public class DirectiveRegistryCheck {
  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      System.err.println("Usage: DirectiveRegistryCheck <directive-name>");
      System.exit(1);
    }
    
    String directiveName = args[0];
    
    // Get the singleton instance of the registry
    SystemDirectiveRegistry registry = SystemDirectiveRegistry.INSTANCE;
    
    // Check if the directive exists
    DirectiveInfo info = registry.get(directiveName);
    
    if (info != null) {
      System.out.println("SUCCESS: Directive '" + directiveName + "' is registered in the system.");
      System.out.println("Class: " + info.instance().getClass().getName());
    } else {
      System.out.println("ERROR: Directive '" + directiveName + "' is NOT registered in the system.");
      
      // List all available directives for reference
      System.out.println("\nAvailable directives:");
      for (DirectiveInfo available : registry.list("default")) {
        System.out.println("- " + available.name());
      }
    }
  }
} 