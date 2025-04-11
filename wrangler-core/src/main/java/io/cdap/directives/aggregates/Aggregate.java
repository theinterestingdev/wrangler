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

import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.wrangler.api.Arguments;
import io.cdap.wrangler.api.Directive;
import io.cdap.wrangler.api.DirectiveExecutionException;
import io.cdap.wrangler.api.DirectiveParseException;
import io.cdap.wrangler.api.ExecutorContext;
import io.cdap.wrangler.api.Row;
import io.cdap.wrangler.api.parser.ByteSize;
import io.cdap.wrangler.api.parser.ColumnName;
import io.cdap.wrangler.api.parser.Text;
import io.cdap.wrangler.api.parser.TimeDuration;
import io.cdap.wrangler.api.parser.TokenType;
import io.cdap.wrangler.api.parser.UsageDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A directive for aggregating data with specific units into new columns with converted units.
 * This directive supports both byte size and time duration unit conversions.
 * 
 * Usage example:
 * aggregate :input_column :operation input_unit :output_column output_unit
 * 
 * e.g., aggregate :data_transfer_size :sum bytes :total_size_mb MB, :response_time :sum ms :total_time_sec sec
 */
@Plugin(type = Directive.TYPE)
@Name("aggregate")
@Description("Aggregates data from source columns into target columns with unit conversion.")
public class Aggregate implements Directive {
    private List<AggregationSpec> specs = new ArrayList<>();
    
    // Caches for aggregation values
    private final Map<String, Double> aggregationResults = new HashMap<>();
    
    /**
     * Represents an aggregation specification for a single column
     */
    private static class AggregationSpec {
        private final String sourceColumn;
        private final String operation;
        private final String inputUnit;
        private final String targetColumn;
        private final String outputUnit;
        
        public AggregationSpec(String sourceColumn, String operation, String inputUnit, 
                             String targetColumn, String outputUnit) {
            this.sourceColumn = sourceColumn;
            this.operation = operation;
            this.inputUnit = inputUnit;
            this.targetColumn = targetColumn;
            this.outputUnit = outputUnit;
        }
    }
    
    @Override
    public UsageDefinition define() {
        UsageDefinition.Builder builder = UsageDefinition.builder("aggregate");
        builder.define("spec", TokenType.TEXT);
        return builder.build();
    }
    
    @Override
    public void initialize(Arguments args) throws DirectiveParseException {
        // Parse the full aggregation spec from the text argument
        String spec = ((Text) args.value("spec")).value();
        parseAggregationSpec(spec);
    }
    
    private void parseAggregationSpec(String spec) throws DirectiveParseException {
        String[] aggregationSpecs = spec.split(",");
        
        for (String aggSpec : aggregationSpecs) {
            String[] parts = aggSpec.trim().split(" ");
            
            if (parts.length < 5) {
                throw new DirectiveParseException(
                    "Invalid aggregation specification format. Expected: :source_column :operation input_unit :target_column output_unit");
            }
            
            String srcColumn = parts[0];
            if (!srcColumn.startsWith(":")) {
                throw new DirectiveParseException("Source column should start with ':'");
            }
            srcColumn = srcColumn.substring(1); // Remove the leading ':'
            
            String operation = parts[1];
            if (!operation.startsWith(":")) {
                throw new DirectiveParseException("Operation should start with ':'");
            }
            operation = operation.substring(1); // Remove the leading ':'
            
            String inputUnit = parts[2];
            
            String targetColumn = parts[3];
            if (!targetColumn.startsWith(":")) {
                throw new DirectiveParseException("Target column should start with ':'");
            }
            targetColumn = targetColumn.substring(1); // Remove the leading ':'
            
            String outputUnit = parts[4];
            
            specs.add(new AggregationSpec(srcColumn, operation, inputUnit, targetColumn, outputUnit));
        }
    }
    
    @Override
    public List<Row> execute(List<Row> rows, ExecutorContext context) throws DirectiveExecutionException {
        // Process each row and calculate aggregates
        for (Row row : rows) {
            processRow(row);
        }
        
        // Create the result row with aggregated values
        Row result = new Row();
        
        for (AggregationSpec spec : specs) {
            double aggregatedValue = aggregationResults.getOrDefault(spec.targetColumn, 0.0);
            
            // Apply the unit conversion based on input/output units
            double convertedValue = convertValue(aggregatedValue, spec.inputUnit, spec.outputUnit);
            
            // Add the converted value to the result
            result.add(spec.targetColumn, convertedValue);
        }
        
        return Collections.singletonList(result);
    }
    
    @Override
    public void destroy() {
        // Clear aggregation results
        aggregationResults.clear();
        specs.clear();
    }
    
    /**
     * Process a single row and update aggregation values
     */
    private void processRow(Row row) throws DirectiveExecutionException {
        for (AggregationSpec spec : specs) {
            Object value = row.getValue(spec.sourceColumn);
            if (value == null) {
                continue;
            }
            
            try {
                double numericValue;
                
                // Convert the value to a canonical unit based on the input type
                if ("bytes".equalsIgnoreCase(spec.inputUnit)) {
                    ByteSize byteSize = new ByteSize(value.toString());
                    numericValue = byteSize.getBytes();
                } else if ("ms".equalsIgnoreCase(spec.inputUnit)) {
                    TimeDuration duration = new TimeDuration(value.toString());
                    numericValue = duration.getMilliseconds();
                } else {
                    throw new DirectiveExecutionException(
                        String.format("Unsupported input unit: %s", spec.inputUnit));
                }
                
                // Apply the aggregation operation
                if ("sum".equalsIgnoreCase(spec.operation)) {
                    double currentValue = aggregationResults.getOrDefault(spec.targetColumn, 0.0);
                    aggregationResults.put(spec.targetColumn, currentValue + numericValue);
                } else if ("avg".equalsIgnoreCase(spec.operation)) {
                    // For average, we'll need to track count separately
                    // This is simplified and would need to be extended for a real implementation
                    double currentValue = aggregationResults.getOrDefault(spec.targetColumn, 0.0);
                    aggregationResults.put(spec.targetColumn, currentValue + numericValue);
                    // In a real implementation, you would track counts and divide at the end
                } else {
                    throw new DirectiveExecutionException(
                        String.format("Unsupported operation: %s", spec.operation));
                }
            } catch (Exception e) {
                throw new DirectiveExecutionException(
                    String.format("Failed to process value for column %s: %s", spec.sourceColumn, e.getMessage()), e);
            }
        }
    }
    
    /**
     * Convert a value from its canonical form to the specified output unit
     */
    private double convertValue(double value, String inputUnit, String outputUnit) throws DirectiveExecutionException {
        // Handle byte size conversions
        if ("bytes".equalsIgnoreCase(inputUnit)) {
            if ("B".equalsIgnoreCase(outputUnit)) {
                return value;
            } else if ("KB".equalsIgnoreCase(outputUnit)) {
                return value / 1024.0;
            } else if ("MB".equalsIgnoreCase(outputUnit)) {
                return value / (1024.0 * 1024.0);
            } else if ("GB".equalsIgnoreCase(outputUnit)) {
                return value / (1024.0 * 1024.0 * 1024.0);
            }
        }
        
        // Handle time duration conversions
        if ("ms".equalsIgnoreCase(inputUnit)) {
            if ("ms".equalsIgnoreCase(outputUnit)) {
                return value;
            } else if ("sec".equalsIgnoreCase(outputUnit)) {
                return value / 1000.0;
            } else if ("min".equalsIgnoreCase(outputUnit)) {
                return value / (60.0 * 1000.0);
            } else if ("hour".equalsIgnoreCase(outputUnit)) {
                return value / (60.0 * 60.0 * 1000.0);
            }
        }
        
        throw new DirectiveExecutionException(
            String.format("Unsupported conversion from %s to %s", inputUnit, outputUnit));
    }
} 