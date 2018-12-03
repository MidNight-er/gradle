/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.taskfactory.TaskIdentity;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationDetails;
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType;
import org.gradle.execution.plan.ExecutionDependencies;
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalPluginIdentifier;
import org.gradle.tooling.internal.provider.BuildClientSubscriptions;
import org.gradle.tooling.internal.provider.events.AbstractTaskResult;
import org.gradle.tooling.internal.provider.events.DefaultFailure;
import org.gradle.tooling.internal.provider.events.DefaultTaskDescriptor;
import org.gradle.tooling.internal.provider.events.DefaultTaskFailureResult;
import org.gradle.tooling.internal.provider.events.DefaultTaskFinishedProgressEvent;
import org.gradle.tooling.internal.provider.events.DefaultTaskSkippedResult;
import org.gradle.tooling.internal.provider.events.DefaultTaskStartedProgressEvent;
import org.gradle.tooling.internal.provider.events.DefaultTaskSuccessResult;
import org.gradle.tooling.internal.provider.events.OperationResultPostProcessor;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Collections.singletonList;

/**
 * Task listener that forwards all receiving events to the client via the provided {@code ProgressEventConsumer} instance.
 *
 * @since 2.5
 */
class ClientForwardingTaskOperationListener extends SubtreeFilteringBuildOperationListener<ExecuteTaskBuildOperationDetails> {

    private final Map<TaskIdentity<?>, DefaultTaskDescriptor> descriptors = new ConcurrentHashMap<>();
    private final OperationResultPostProcessor operationResultPostProcessor;
    private final TaskExecutionGraphTracker taskExecutionGraphTracker;
    private final TaskOriginTracker taskOriginTracker;

    @Nullable
    private final TransformOperationTracker transformOperationTracker;

    ClientForwardingTaskOperationListener(ProgressEventConsumer eventConsumer, BuildClientSubscriptions clientSubscriptions, BuildOperationListener delegate,
                                          OperationResultPostProcessor operationResultPostProcessor, TaskExecutionGraphTracker taskExecutionGraphTracker, TaskOriginTracker taskOriginTracker,
                                          @Nullable TransformOperationTracker transformOperationTracker) {
        super(eventConsumer, clientSubscriptions, delegate, OperationType.TASK, ExecuteTaskBuildOperationDetails.class);
        this.operationResultPostProcessor = operationResultPostProcessor;
        this.taskExecutionGraphTracker = taskExecutionGraphTracker;
        this.taskOriginTracker = taskOriginTracker;
        this.transformOperationTracker = transformOperationTracker;
    }

    @Override
    protected InternalOperationStartedProgressEvent toStartedEvent(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent, ExecuteTaskBuildOperationDetails details) {
        return new DefaultTaskStartedProgressEvent(startEvent.getStartTime(), toTaskDescriptor(buildOperation, details.getTask()));
    }

    @Override
    protected InternalOperationFinishedProgressEvent toFinishedEvent(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent, ExecuteTaskBuildOperationDetails details) {
        TaskInternal task = details.getTask();
        AbstractTaskResult taskResult = operationResultPostProcessor.process(toTaskResult(task, finishEvent), buildOperation.getId());
        return new DefaultTaskFinishedProgressEvent(finishEvent.getEndTime(), toTaskDescriptor(buildOperation, task), taskResult);
    }

    private DefaultTaskDescriptor toTaskDescriptor(BuildOperationDescriptor buildOperation, TaskInternal task) {
        return descriptors.computeIfAbsent(task.getTaskIdentity(), taskIdentity -> {
            Object id = buildOperation.getId();
            String taskIdentityPath = buildOperation.getName();
            String displayName = buildOperation.getDisplayName();
            String taskPath = task.getIdentityPath().toString();
            Object parentId = eventConsumer.findStartedParentId(buildOperation);
            InternalPluginIdentifier originPlugin = taskOriginTracker.getOriginPlugin(taskIdentity);
            return new DefaultTaskDescriptor(id, taskIdentityPath, taskPath, displayName, parentId, computeTaskDependencies(task), originPlugin);
        });
    }

    private static AbstractTaskResult toTaskResult(TaskInternal task, OperationFinishEvent finishEvent) {
        TaskStateInternal state = task.getState();
        long startTime = finishEvent.getStartTime();
        long endTime = finishEvent.getEndTime();
        ExecuteTaskBuildOperationType.Result result = (ExecuteTaskBuildOperationType.Result) finishEvent.getResult();
        boolean incremental = result != null && result.isIncremental();

        if (state.getUpToDate()) {
            return new DefaultTaskSuccessResult(startTime, endTime, true, state.isFromCache(), state.getSkipMessage(), incremental, Collections.emptyList());
        } else if (state.getSkipped()) {
            return new DefaultTaskSkippedResult(startTime, endTime, state.getSkipMessage(), incremental);
        } else {
            List<String> executionReasons = result != null ? result.getUpToDateMessages() : null;
            Throwable failure = finishEvent.getFailure();
            if (failure == null) {
                return new DefaultTaskSuccessResult(startTime, endTime, false, state.isFromCache(), "SUCCESS", incremental, executionReasons);
            } else {
                return new DefaultTaskFailureResult(startTime, endTime, singletonList(DefaultFailure.fromThrowable(failure)), incremental, executionReasons);
            }
        }
    }

    private Set<InternalOperationDescriptor> computeTaskDependencies(TaskInternal task) {
        TaskExecutionGraphInternal taskExecutionGraph = taskExecutionGraphTracker.getTaskExecutionGraph(task);
        ExecutionDependencies executionDependencies = taskExecutionGraph.getExecutionDependencies(task);
        Set<InternalOperationDescriptor> result = new LinkedHashSet<>();
        executionDependencies.getTasks().stream()
            .map(dependency -> ((TaskInternal) dependency).getTaskIdentity())
            .map(descriptors::get)
            .filter(Objects::nonNull)
            .forEach(result::add);
        if (transformOperationTracker != null) {
            executionDependencies.getTransformations().stream()
                .map(transformOperationTracker::findOperationDescriptor)
                .filter(Objects::nonNull)
                .forEach(result::add);
        }
        return result;
    }

}
