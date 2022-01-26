/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.tasks.diagnostics;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.reporting.Reporting;
import org.gradle.api.reporting.SingleFileReport;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.diagnostics.configurations.ConfigurationReports;
import org.gradle.api.tasks.diagnostics.internal.configurations.model.ConfigurationReportModelFactory;
import org.gradle.api.tasks.diagnostics.internal.configurations.ConfigurationReportsImpl;
import org.gradle.api.tasks.diagnostics.internal.configurations.model.ConfigurationReportModel;
import org.gradle.api.tasks.diagnostics.internal.configurations.renderer.AbstractWritableConfigurationReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.configurations.renderer.ConsoleConfigurationReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.configurations.spec.AbstractConfigurationReportSpec;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.util.internal.ClosureBackedAction;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;
import java.io.FileWriter;

/**
 * Base class for reporting tasks which display information about attributes and related data associated to a variant/configuration.
 *
 * This class implements {@link Reporting} to make configuring additional file output formats simple.
 *
 * @since 7.5
 */
@Incubating
@DisableCachingByDefault(because = "Produces only non-cacheable console output")
public abstract class AbstractConfigurationReportTask extends DefaultTask implements Reporting<ConfigurationReports> {
    private final ConfigurationReports reports;

    @Inject protected abstract ObjectFactory getObjectFactory();
    @Inject protected abstract StyledTextOutputFactory getTextOutputFactory();
    @Inject protected abstract FileResolver getFileResolver();

    protected abstract AbstractConfigurationReportSpec buildReportSpec();

    public AbstractConfigurationReportTask() {
        reports = getObjectFactory().newInstance(ConfigurationReportsImpl.class, this);
    }

    /**
     * The reports to be generated by this task.
     */
    @Override
    @Nested
    public final ConfigurationReports getReports() {
        return reports;
    }

    /**
     * Configures the reports to be generated by this task.
     *
     * The contained reports can be configured by task name and closures.
     *
     * For example, to configure the "outgoingVariants" report to emit a JSON file:
     *
     * <pre>
     * outgoingVariants {
     *   reports {
     *     json {
     *       required = true
     *       outputLocation.set(file("build/reports/outgoingVariants.js")
     *     }
     *   }
     * }
     * </pre>
     *
     * @param closure The configuration
     * @return The reports container
     */
    @Override
    @SuppressWarnings("rawtypes")
    public ConfigurationReports reports(@DelegatesTo(value = ConfigurationReports.class, strategy = Closure.DELEGATE_FIRST) Closure closure) {
        return reports(new ClosureBackedAction<>(closure));
    }

    /**
     * Configures the reports to be generated by this task.
     *
     * The contained reports can be configured by task name and closures.
     *
     * For example, to configure the "resolvableConfigurations" report to emit a JSON file:
     *
     * <pre>
     * resolvableConfigurations {
     *   reports {
     *     json {
     *       required = true
     *       outputLocation.set(file("build/reports/confs/resolvableConfigurations.js"))
     *     }
     *   }
     * }
     * </pre>
     *
     * @param configureAction The configuration
     * @return The reports container
     */
    @Override
    public ConfigurationReports reports(Action<? super ConfigurationReports> configureAction) {
        configureAction.execute(reports);
        return reports;
    }

    @TaskAction
    public final void report() {
        final AbstractConfigurationReportSpec reportSpec = buildReportSpec();
        final ConfigurationReportModel reportModel = buildReportModel(getProject());

        reportToConsole(reportSpec, reportModel);
    }

    private void reportToFile(SingleFileReport report, AbstractConfigurationReportSpec reportSpec, ConfigurationReportModel reportModel) {
        final File outputFile = report.getOutputLocation().get().getAsFile();
        final AbstractWritableConfigurationReportRenderer renderer = buildToFileRenderer(report, reportSpec);

        try (FileWriter fw = new FileWriter(outputFile)) {
            renderer.render(reportModel, fw);
        } catch (Exception e) {
            throw new GradleException("Failed to write '" + report.getName() +  "' report to " + outputFile, e);
        }
    }

    private void reportToConsole(AbstractConfigurationReportSpec reportSpec, ConfigurationReportModel reportModel) {
        final ConsoleConfigurationReportRenderer renderer = new ConsoleConfigurationReportRenderer(reportSpec);
        final StyledTextOutput output = getTextOutputFactory().create(getClass());
        renderer.render(reportModel, output);
    }

    private AbstractWritableConfigurationReportRenderer buildToFileRenderer(SingleFileReport report, AbstractConfigurationReportSpec reportSpec) {
        switch (report.getName()) {
            default:
                throw new IllegalArgumentException("Unknown report type: " + report.getName());
        }
    }

    private ConfigurationReportModel buildReportModel(Project project) {
        final ConfigurationReportModelFactory factory = new ConfigurationReportModelFactory(getFileResolver());
        return factory.buildForProject(project);
    }
}
