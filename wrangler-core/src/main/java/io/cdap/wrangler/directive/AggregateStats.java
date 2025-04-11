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

package io.cdap.wrangler.directive;

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
 * This implementation works with the RecipePipelineExecutor's row-by-row processing model.
 */
@Plugin(type = Directive.TYPE)
@Name("aggregate-stats")
@Description("Aggregates size and time values into total size and total time columns.")
public class AggregateStats implements Directive {
    private String sizeColumn;
    private String timeColumn;
    private String totalSizeColumn;
    private String totalTimeColumn;
    // Static aggregation accumulators
    private static long totalSizeBytes = 0L;
    private static long totalTimeMillis = 0L;
    // Flag to track if we're on the first row
    private static boolean isFirstRow = true;
    // Flag to track if we've aggregated at least one row
    private static boolean hasAggregated = false;

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
        
        // Reset the static counters when initialized
        if (isFirstRow) {
            totalSizeBytes = 0L;
            totalTimeMillis = 0L;
            hasAggregated = false;
            isFirstRow = false;
        }
    }

    @Override
    public List<Row> execute(List<Row> rows, ExecutorContext context) throws DirectiveExecutionException {
        // When RecipePipelineExecutor calls this method, it will always have exactly one row
        if (rows.isEmpty()) {
            return rows;
        }
        
        Row row = rows.get(0);
        try {
            // Add to the running totals
            Object sizeVal = row.getValue(sizeColumn);
            if (sizeVal != null) {
                ByteSize size = new ByteSize(sizeVal.toString());
                totalSizeBytes += size.getBytes();
                hasAggregated = true;
            }
            
            Object timeVal = row.getValue(timeColumn);
            if (timeVal != null) {
                TimeDuration duration = new TimeDuration(timeVal.toString());
                totalTimeMillis += duration.getMilliseconds();
                hasAggregated = true;
            }
            
            // Replace current row with an aggregated row
            Row result = new Row();
            result.add(totalSizeColumn, formatBytes(totalSizeBytes));
            result.add(totalTimeColumn, formatMilliseconds(totalTimeMillis));
            
            return Collections.singletonList(result);
        } catch (Exception e) {
            throw new DirectiveExecutionException(
                String.format("Failed to process value: %s", e.getMessage()), e);
        }
    }

    @Override
    public void destroy() {
        // Reset static variables when the directive is destroyed
        totalSizeBytes = 0L;
        totalTimeMillis = 0L;
        isFirstRow = true;
        hasAggregated = false;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char unit = "KMGTPE".charAt(exp - 1);
        return String.format("%.2f%sB", bytes / Math.pow(1024, exp), unit);
    }

    private String formatMilliseconds(long milliseconds) {
        // Handle negative values
        if (milliseconds < 0) {
            return "-" + formatMilliseconds(Math.abs(milliseconds));
        }
        
        if (milliseconds < 1000) {
            return milliseconds + "ms";
        }
        double seconds = milliseconds / 1000.0;
        if (seconds < 60) {
            return String.format("%.2fs", seconds);
        }
        double minutes = seconds / 60;
        if (minutes < 60) {
            return String.format("%.2fm", minutes);
        }
        return String.format("%.2fh", minutes / 60);
    }
}
