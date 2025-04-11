/*
 * Copyright © 2017-2019 Cask Data, Inc.
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
import io.cdap.wrangler.api.parser.TimeDuration;
import io.cdap.wrangler.api.parser.TokenType;
import io.cdap.wrangler.api.parser.UsageDefinition;

import java.util.Collections;
import java.util.List;

/**
 * A directive for aggregating byte size and time duration values.
 * This directive takes source columns containing byte sizes and time durations,
 * and produces target columns with aggregated values.
 */
@Plugin(type = Directive.TYPE)
@Name("aggregate-stats")
@Description("Aggregates size and time values into total size and total time columns.")
public class AggregateStats implements Directive {
    private String sizeColumn;
    private String timeColumn;
    private String totalSizeColumn;
    private String totalTimeColumn;
    
    // Constants for unit conversions
    private static final double BYTES_PER_MB = 1024.0 * 1024.0;
    private static final double MS_PER_SECOND = 1000.0;
    
    // Storage for aggregation across rows
    private long currentTotalSizeBytes = 0L;
    private long currentTotalTimeMillis = 0L;
    
    @Override
    public UsageDefinition define() {
        UsageDefinition.Builder builder = UsageDefinition.builder("aggregate-stats");
        builder.define("source-size-column", TokenType.COLUMN_NAME);
        builder.define("source-time-column", TokenType.COLUMN_NAME);
        builder.define("total-size-column", TokenType.COLUMN_NAME);
        builder.define("total-time-column", TokenType.COLUMN_NAME);
        return builder.build();
    }

    @Override
    public void initialize(Arguments args) throws DirectiveParseException {
        this.sizeColumn = ((ColumnName) args.value("source-size-column")).value();
        this.timeColumn = ((ColumnName) args.value("source-time-column")).value();
        this.totalSizeColumn = ((ColumnName) args.value("total-size-column")).value();
        this.totalTimeColumn = ((ColumnName) args.value("total-time-column")).value();
        
        // Reset aggregation variables on initialization
        this.currentTotalSizeBytes = 0L;
        this.currentTotalTimeMillis = 0L;
    }

    @Override
    public List<Row> execute(List<Row> rows, ExecutorContext context) throws DirectiveExecutionException {
        // Special case: input is empty
        if (rows.isEmpty()) {
            Row result = new Row();
            result.add(totalSizeColumn, String.format("%.2f MB", 0.0));
            result.add(totalTimeColumn, String.format("%.2fs", 0.0));
            return Collections.singletonList(result);
        }
        
        // Get the current row to process
        Row currentRow = rows.get(0);
        
        // Process the current row values and add to running totals
        try {
            Object sizeVal = currentRow.getValue(sizeColumn);
            if (sizeVal != null) {
                ByteSize size = new ByteSize(sizeVal.toString());
                currentTotalSizeBytes += size.getBytes();
            }
            
            Object timeVal = currentRow.getValue(timeColumn);
            if (timeVal != null) {
                TimeDuration duration = new TimeDuration(timeVal.toString());
                currentTotalTimeMillis += duration.getMilliseconds();
            }
        } catch (Exception e) {
            throw new DirectiveExecutionException(
                String.format("Failed to process value: %s", e.getMessage()), e);
        }
        
        // Create a single result row with the aggregated values
        // Convert to appropriate target units (MB for size, seconds for time)
        double totalSizeMB = currentTotalSizeBytes / BYTES_PER_MB; 
        double totalTimeSeconds = currentTotalTimeMillis / MS_PER_SECOND;
        
        Row result = new Row();
        result.add(totalSizeColumn, String.format("%.2f MB", totalSizeMB));
        result.add(totalTimeColumn, String.format("%.2fs", totalTimeSeconds));
        
        // Return the aggregate result for only the current row, 
        // causing RecipePipelineExecutor to include this row in the final results 
        return Collections.singletonList(result);
    }

    @Override
    public void destroy() {
        // Reset aggregation variables
        this.currentTotalSizeBytes = 0L;
        this.currentTotalTimeMillis = 0L;
    }
}
