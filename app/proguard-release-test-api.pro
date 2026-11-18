# Public runtime entry points used by the separate release instrumentation APK.
# Retain only the concrete Kotlin/Koin/AndroidX API references lost to whole-app
# optimization; implementation bodies remain optimized. No package-wide keeps.

-keep,allowoptimization class androidx.concurrent.futures.CallbackToFutureAdapter {
    com.google.common.util.concurrent.ListenableFuture getFuture(androidx.concurrent.futures.CallbackToFutureAdapter$Resolver);
}
-keep,allowoptimization class androidx.concurrent.futures.SuspendToFutureAdapter {
    androidx.concurrent.futures.SuspendToFutureAdapter INSTANCE;
    com.google.common.util.concurrent.ListenableFuture launchFuture$default(androidx.concurrent.futures.SuspendToFutureAdapter,kotlin.coroutines.CoroutineContext,boolean,kotlin.jvm.functions.Function2,int,java.lang.Object);
}
-keep,allowoptimization class androidx.core.os.ConfigurationCompat {
    androidx.core.os.LocaleListCompat getLocales(android.content.res.Configuration);
    void setLocales(android.content.res.Configuration,androidx.core.os.LocaleListCompat);
}
-keep,allowoptimization class androidx.core.view.ViewConfigurationCompat {
    float getScaledHorizontalScrollFactor(android.view.ViewConfiguration,android.content.Context);
    float getScaledVerticalScrollFactor(android.view.ViewConfiguration,android.content.Context);
}
-keep,allowoptimization class androidx.core.view.ViewGroupKt {
    kotlin.sequences.Sequence getChildren(android.view.ViewGroup);
}
-keep,allowoptimization class androidx.lifecycle.Lifecycle {
    void addObserver(androidx.lifecycle.LifecycleObserver);
    void removeObserver(androidx.lifecycle.LifecycleObserver);
}
-keep,allowoptimization class androidx.lifecycle.LifecycleOwner {
    androidx.lifecycle.Lifecycle getLifecycle();
}
-keep,allowoptimization class androidx.lifecycle.ViewTreeLifecycleOwner {
    androidx.lifecycle.LifecycleOwner get(android.view.View);
}
-keep,allowoptimization class androidx.tracing.Trace {
    void endSection();
    void forceEnableAppTracing();
}
-keep,allowoptimization class kotlin.KotlinNothingValueException {
    <init>();
}
-keep,allowoptimization class kotlin.LazyKt {
}
-keep,allowoptimization class kotlin.LazyKt__LazyJVMKt {
    kotlin.Lazy lazy(kotlin.jvm.functions.Function0);
}
-keep,allowoptimization class kotlin.NoWhenBranchMatchedException {
    <init>();
}
-keep,allowoptimization class kotlin.NotImplementedError {
    <init>(java.lang.String);
}
-keep,allowoptimization class kotlin.Result$Companion {
}
-keep,allowoptimization class kotlin.Result {
    boolean isFailure-impl(java.lang.Object);
    java.lang.Object constructor-impl(java.lang.Object);
    kotlin.Result$Companion Companion;
}
-keep,allowoptimization class kotlin.ResultKt {
    java.lang.Object createFailure(java.lang.Throwable);
}
-keep,allowoptimization class kotlin.annotation.AnnotationRetention {
    kotlin.annotation.AnnotationRetention BINARY;
    kotlin.annotation.AnnotationRetention SOURCE;
}
-keep,allowoptimization class kotlin.collections.AbstractIterator {
    <init>();
    void done();
    void setNext(java.lang.Object);
}
-keep,allowoptimization class kotlin.collections.ArraysKt {
}
-keep,allowoptimization class kotlin.collections.ArraysKt___ArraysKt {
    java.lang.String joinToString$default(java.lang.Object[],java.lang.CharSequence,java.lang.CharSequence,java.lang.CharSequence,int,java.lang.CharSequence,kotlin.jvm.functions.Function1,int,java.lang.Object);
    java.util.List toList(java.lang.Object[]);
}
-keep,allowoptimization class kotlin.collections.CollectionsKt {
}
-keep,allowoptimization class kotlin.collections.CollectionsKt__CollectionsKt {
    java.util.List emptyList();
}
-keep,allowoptimization class kotlin.collections.CollectionsKt__IterablesKt {
    java.util.List flatten(java.lang.Iterable);
}
-keep,allowoptimization class kotlin.collections.CollectionsKt__MutableCollectionsKt {
    boolean addAll(java.util.Collection,java.lang.Iterable);
}
-keep,allowoptimization class kotlin.collections.CollectionsKt___CollectionsKt {
    int count(java.lang.Iterable);
    java.lang.String joinToString$default(java.lang.Iterable,java.lang.CharSequence,java.lang.CharSequence,java.lang.CharSequence,int,java.lang.CharSequence,kotlin.jvm.functions.Function1,int,java.lang.Object);
    java.util.List plus(java.util.Collection,java.lang.Iterable);
    java.util.List takeLast(java.util.List,int);
    kotlin.sequences.Sequence asSequence(java.lang.Iterable);
}
-keep,allowoptimization class kotlin.collections.MapsKt {
}
-keep,allowoptimization class kotlin.collections.MapsKt__MapsJVMKt {
    int mapCapacity(int);
}
-keep,allowoptimization class kotlin.collections.MapsKt__MapsKt {
    java.util.Map emptyMap();
    java.util.Map mutableMapOf(kotlin.Pair[]);
}
-keep,allowoptimization class kotlin.comparisons.ComparisonsKt {
}
-keep,allowoptimization class kotlin.comparisons.ComparisonsKt__ComparisonsKt {
    int compareValues(java.lang.Comparable,java.lang.Comparable);
    int compareValuesBy(java.lang.Object,java.lang.Object,kotlin.jvm.functions.Function1[]);
}
-keep,allowoptimization class kotlin.coroutines.ContinuationInterceptor$DefaultImpls {
    void releaseInterceptedContinuation(kotlin.coroutines.ContinuationInterceptor,kotlin.coroutines.Continuation);
}
-keep,allowoptimization class kotlin.coroutines.ContinuationInterceptor {
    kotlin.coroutines.Continuation interceptContinuation(kotlin.coroutines.Continuation);
    kotlin.coroutines.ContinuationInterceptor$Key Key;
}
-keep,allowoptimization class kotlin.coroutines.CoroutineContext$Element$DefaultImpls {
    java.lang.Object fold(kotlin.coroutines.CoroutineContext$Element,java.lang.Object,kotlin.jvm.functions.Function2);
    kotlin.coroutines.CoroutineContext minusKey(kotlin.coroutines.CoroutineContext$Element,kotlin.coroutines.CoroutineContext$Key);
    kotlin.coroutines.CoroutineContext plus(kotlin.coroutines.CoroutineContext$Element,kotlin.coroutines.CoroutineContext);
    kotlin.coroutines.CoroutineContext$Element get(kotlin.coroutines.CoroutineContext$Element,kotlin.coroutines.CoroutineContext$Key);
}
-keep,allowoptimization class kotlin.coroutines.intrinsics.IntrinsicsKt {
}
-keep,allowoptimization class kotlin.coroutines.intrinsics.IntrinsicsKt__IntrinsicsKt {
    java.lang.Object getCOROUTINE_SUSPENDED();
}
-keep,allowoptimization class kotlin.coroutines.jvm.internal.DebugProbesKt {
    void probeCoroutineSuspended(kotlin.coroutines.Continuation);
}
-keep,allowoptimization class kotlin.coroutines.jvm.internal.SpillingKt {
    java.lang.Object nullOutSpilledVariable(java.lang.Object);
}
-keep,allowoptimization class kotlin.coroutines.jvm.internal.SuspendFunction {
}
-keep,allowoptimization class kotlin.enums.EnumEntriesKt {
    kotlin.enums.EnumEntries enumEntries(java.lang.Enum[]);
}
-keep,allowoptimization class kotlin.jvm.internal.FunctionReferenceImpl {
    <init>(int,java.lang.Object,java.lang.Class,java.lang.String,java.lang.String,int);
}
-keep,allowoptimization class kotlin.jvm.internal.InlineMarker {
    void finallyEnd(int);
    void finallyStart(int);
}
-keep,allowoptimization class kotlin.jvm.internal.Intrinsics {
    void checkNotNull(java.lang.Object,java.lang.String);
    void checkNotNullExpressionValue(java.lang.Object,java.lang.String);
    void checkNotNullParameter(java.lang.Object,java.lang.String);
    void needClassReification();
    void reifiedOperationMarker(int,java.lang.String);
}
-keep,allowoptimization class kotlin.jvm.internal.Ref$BooleanRef {
    <init>();
}
-keep,allowoptimization class kotlin.jvm.internal.Ref$FloatRef {
    <init>();
}
-keep,allowoptimization class kotlin.jvm.internal.StringCompanionObject {
    kotlin.jvm.internal.StringCompanionObject INSTANCE;
}
-keep,allowoptimization class kotlin.math.MathKt {
}
-keep,allowoptimization class kotlin.math.MathKt__MathJVMKt {
    long roundToLong(float);
}
-keep,allowoptimization class kotlin.ranges.LongProgression {
    long getFirst();
    long getLast();
}
-keep,allowoptimization class kotlin.ranges.LongRange {
    <init>(long,long);
}
-keep,allowoptimization class kotlin.sequences.SequencesKt {
}
-keep,allowoptimization class kotlin.sequences.SequencesKt___SequencesKt {
    kotlin.sequences.Sequence filter(kotlin.sequences.Sequence,kotlin.jvm.functions.Function1);
    kotlin.sequences.Sequence zipWithNext(kotlin.sequences.Sequence,kotlin.jvm.functions.Function2);
}
-keep,allowoptimization class kotlin.text.Regex {
    java.lang.String replace(java.lang.CharSequence,java.lang.String);
}
-keep,allowoptimization class kotlin.text.StringsKt {
}
-keep,allowoptimization class kotlin.text.StringsKt__StringsJVMKt {
    boolean startsWith$default(java.lang.String,java.lang.String,boolean,int,java.lang.Object);
}
-keep,allowoptimization class kotlin.text.StringsKt__StringsKt {
    boolean contains$default(java.lang.CharSequence,java.lang.CharSequence,boolean,int,java.lang.Object);
}
-keep,allowoptimization class kotlin.time.AbstractLongTimeSource {
    <init>(kotlin.time.DurationUnit);
}
-keep,allowoptimization class kotlin.time.Duration$Companion {
    long getINFINITE-UwyO8pc();
    long parse-UwyO8pc(java.lang.String);
}
-keep,allowoptimization class kotlin.time.Duration {
    boolean isNegative-impl(long);
    kotlin.time.Duration box-impl(long);
    kotlin.time.Duration$Companion Companion;
    long unbox-impl();
}
-keep,allowoptimization class kotlin.time.DurationKt {
    long toDuration(int,kotlin.time.DurationUnit);
    long toDuration(long,kotlin.time.DurationUnit);
}
-keep,allowoptimization class kotlin.time.TimeSource$WithComparableMarks {
}
-keep,allowoptimization class kotlinx.coroutines.AbstractCoroutine {
    <init>(kotlin.coroutines.CoroutineContext,boolean,boolean);
    void start(kotlinx.coroutines.CoroutineStart,java.lang.Object,kotlin.jvm.functions.Function2);
}
-keep,allowoptimization class kotlinx.coroutines.BuildersKt {
    *** runBlocking*(...);
    java.lang.Object runBlocking(kotlin.coroutines.CoroutineContext,kotlin.jvm.functions.Function2);
    java.lang.Object withContext(kotlin.coroutines.CoroutineContext,kotlin.jvm.functions.Function2,kotlin.coroutines.Continuation);
    kotlinx.coroutines.Job launch$default(kotlinx.coroutines.CoroutineScope,kotlin.coroutines.CoroutineContext,kotlinx.coroutines.CoroutineStart,kotlin.jvm.functions.Function2,int,java.lang.Object);
}
-keep,allowoptimization class kotlinx.coroutines.CancellableContinuation {
    void invokeOnCancellation(kotlin.jvm.functions.Function1);
    void resume(java.lang.Object,kotlin.jvm.functions.Function1);
    void resumeUndispatched(kotlinx.coroutines.CoroutineDispatcher,java.lang.Object);
}
-keep,allowoptimization class kotlinx.coroutines.CancellableContinuationImpl {
    <init>(kotlin.coroutines.Continuation,int);
}
-keep,allowoptimization class kotlinx.coroutines.CancellableContinuationKt {
    void disposeOnCancellation(kotlinx.coroutines.CancellableContinuation,kotlinx.coroutines.DisposableHandle);
}
-keep,allowoptimization class kotlinx.coroutines.CompletableJob {
    boolean complete();
}
-keep,allowoptimization class kotlinx.coroutines.CoroutineDispatcher {
    kotlin.coroutines.Continuation interceptContinuation(kotlin.coroutines.Continuation);
    void dispatch(kotlin.coroutines.CoroutineContext,java.lang.Runnable);
}
-keep,allowoptimization class kotlinx.coroutines.CoroutineExceptionHandler$Key {
}
-keep,allowoptimization class kotlinx.coroutines.CoroutineExceptionHandler {
    kotlinx.coroutines.CoroutineExceptionHandler$Key Key;
    void handleException(kotlin.coroutines.CoroutineContext,java.lang.Throwable);
}
-keep,allowoptimization class kotlinx.coroutines.CoroutineExceptionHandlerKt {
    void handleCoroutineException(kotlin.coroutines.CoroutineContext,java.lang.Throwable);
}
-keep,allowoptimization class kotlinx.coroutines.CoroutineScopeKt {
    boolean isActive(kotlinx.coroutines.CoroutineScope);
    kotlinx.coroutines.CoroutineScope CoroutineScope(kotlin.coroutines.CoroutineContext);
    void cancel$default(kotlinx.coroutines.CoroutineScope,java.util.concurrent.CancellationException,int,java.lang.Object);
}
-keep,allowoptimization class kotlinx.coroutines.DebugKt {
    boolean getRECOVER_STACK_TRACES();
}
-keep,allowoptimization class kotlinx.coroutines.DefaultExecutorKt {
    kotlinx.coroutines.Delay getDefaultDelay();
}
-keep,allowoptimization class kotlinx.coroutines.Delay {
    java.lang.Object delay(long,kotlin.coroutines.Continuation);
    void scheduleResumeAfterDelay(long,kotlinx.coroutines.CancellableContinuation);
}
-keep,allowoptimization class kotlinx.coroutines.DelayKt {
    java.lang.Object delay(long,kotlin.coroutines.Continuation);
}
-keep,allowoptimization class kotlinx.coroutines.DelayWithTimeoutDiagnostics {
}
-keep,allowoptimization class kotlinx.coroutines.Dispatchers {
    kotlinx.coroutines.CoroutineDispatcher getDefault();
    kotlinx.coroutines.MainCoroutineDispatcher getMain();
}
-keep,allowoptimization class kotlinx.coroutines.ExecutorsKt {
    kotlinx.coroutines.CoroutineDispatcher from(java.util.concurrent.Executor);
}
-keep,allowoptimization class kotlinx.coroutines.Job {
    kotlinx.coroutines.Job$Key Key;
}
-keep,allowoptimization class kotlinx.coroutines.JobKt {
    java.lang.Object cancelAndJoin(kotlinx.coroutines.Job,kotlin.coroutines.Continuation);
    kotlinx.coroutines.CompletableJob Job$default(kotlinx.coroutines.Job,int,java.lang.Object);
}
-keep,allowoptimization class kotlinx.coroutines.JobSupport {
    java.lang.Throwable getCompletionCause();
    java.lang.Throwable getCompletionExceptionOrNull();
    kotlinx.coroutines.selects.SelectClause0 getOnJoin();
}
-keep,allowoptimization class kotlinx.coroutines.MainCoroutineDispatcher {
    <init>();
    kotlinx.coroutines.MainCoroutineDispatcher getImmediate();
}
-keep,allowoptimization class kotlinx.coroutines.TimeoutKt {
    java.lang.Object withTimeout(long,kotlin.jvm.functions.Function2,kotlin.coroutines.Continuation);
    java.lang.Object withTimeout-KLykuaI(long,kotlin.jvm.functions.Function2,kotlin.coroutines.Continuation);
}
-keep,allowoptimization class kotlinx.coroutines.YieldContext$Key {
}
-keep,allowoptimization class kotlinx.coroutines.YieldContext {
    kotlinx.coroutines.YieldContext$Key Key;
}
-keep,allowoptimization class kotlinx.coroutines.YieldKt {
    java.lang.Object yield(kotlin.coroutines.Continuation);
}
-keep,allowoptimization class kotlinx.coroutines.channels.Channel {
}
-keep,allowoptimization class kotlinx.coroutines.channels.ChannelKt {
    kotlinx.coroutines.channels.Channel Channel$default(int,kotlinx.coroutines.channels.BufferOverflow,kotlin.jvm.functions.Function1,int,java.lang.Object);
}
-keep,allowoptimization class kotlinx.coroutines.channels.ReceiveChannel {
    kotlinx.coroutines.selects.SelectClause1 getOnReceive();
}
-keep,allowoptimization class kotlinx.coroutines.debug.internal.DebugProbesImpl {
    boolean isInstalled$kotlinx_coroutines_debug();
    kotlinx.coroutines.debug.internal.DebugProbesImpl INSTANCE;
    void dumpCoroutines(java.io.PrintStream);
    void install$kotlinx_coroutines_core();
    void uninstall$kotlinx_coroutines_core();
}
-keep,allowoptimization class kotlinx.coroutines.flow.FlowKt {
    java.lang.Object toList$default(kotlinx.coroutines.flow.Flow,java.util.List,kotlin.coroutines.Continuation,int,java.lang.Object);
    kotlinx.coroutines.flow.Flow flow(kotlin.jvm.functions.Function2);
}
-keep,allowoptimization class kotlinx.coroutines.internal.CoroutineExceptionHandlerImplKt {
    void ensurePlatformExceptionHandlerLoaded(kotlinx.coroutines.CoroutineExceptionHandler);
}
-keep,allowoptimization class kotlinx.coroutines.internal.ExceptionSuccessfullyProcessed {
    kotlinx.coroutines.internal.ExceptionSuccessfullyProcessed INSTANCE;
}
-keep,allowoptimization class kotlinx.coroutines.internal.MainDispatcherFactory {
    int getLoadPriority();
    java.lang.String hintOnError();
}
-keep,allowoptimization class kotlinx.coroutines.internal.MainDispatchersKt {
    boolean isMissing(kotlinx.coroutines.MainCoroutineDispatcher);
    kotlinx.coroutines.MainCoroutineDispatcher tryCreateDispatcher(kotlinx.coroutines.internal.MainDispatcherFactory,java.util.List);
}
-keep,allowoptimization class kotlinx.coroutines.internal.MissingMainCoroutineDispatcherFactory {
    kotlinx.coroutines.internal.MissingMainCoroutineDispatcherFactory INSTANCE;
}
-keep,allowoptimization class kotlinx.coroutines.internal.StackTraceRecoveryKt {
    java.lang.Throwable unwrapImpl(java.lang.Throwable);
}
-keep,allowoptimization class kotlinx.coroutines.internal.ThreadSafeHeap {
    <init>();
    boolean isEmpty();
    boolean remove(kotlinx.coroutines.internal.ThreadSafeHeapNode);
    kotlinx.coroutines.internal.ThreadSafeHeapNode find(kotlin.jvm.functions.Function1);
    kotlinx.coroutines.internal.ThreadSafeHeapNode firstImpl();
    kotlinx.coroutines.internal.ThreadSafeHeapNode removeAtImpl(int);
    kotlinx.coroutines.internal.ThreadSafeHeapNode removeFirstOrNull();
    void addLast(kotlinx.coroutines.internal.ThreadSafeHeapNode);
}
-keep,allowoptimization class kotlinx.coroutines.internal.ThreadSafeHeapNode {
}
-keep,allowoptimization class kotlinx.coroutines.selects.OnTimeoutKt {
    void onTimeout-8Mi8wO0(kotlinx.coroutines.selects.SelectBuilder,long,kotlin.jvm.functions.Function1);
}
-keep,allowoptimization class kotlinx.coroutines.selects.SelectBuilder {
    void invoke(kotlinx.coroutines.selects.SelectClause0,kotlin.jvm.functions.Function1);
    void invoke(kotlinx.coroutines.selects.SelectClause1,kotlin.jvm.functions.Function2);
}
-keep,allowoptimization class kotlinx.coroutines.selects.SelectClause0 {
}
-keep,allowoptimization class kotlinx.coroutines.selects.SelectClause1 {
}
-keep,allowoptimization class kotlinx.coroutines.selects.SelectImplementation {
    java.lang.Object doSelect(kotlin.coroutines.Continuation);
}
-keep,allowoptimization class org.koin.core.Koin {
    org.koin.core.registry.ScopeRegistry getScopeRegistry();
}
-keep,allowoptimization class org.koin.core.registry.ScopeRegistry {
    org.koin.core.scope.Scope getRootScope();
}
-keep,allowoptimization class org.koin.mp.KoinPlatform {
    org.koin.core.Koin getKoin();
    org.koin.mp.KoinPlatform INSTANCE;
}

# This ID is read directly by the guarded location fallback test.
-keep,allowoptimization class me.rerere.rikkahub.R$string {
    int placeholder_location_unavailable;
}

# Suspend state machines in the test APK extend this shared Kotlin base class.
-keep,allowoptimization class kotlin.coroutines.jvm.internal.SuspendLambda {
    <init>(...);
}

# Real STT runtime smoke test calls these app-owned APIs from the separate test APK.
-keep,allowoptimization class me.rerere.asr.local.SherpaSttRuntime { public *; }
-keep,allowoptimization,includedescriptorclasses class me.rerere.asr.local.InstalledSherpaModel { public *; }
-keep,allowoptimization enum me.rerere.asr.local.SherpaModelFamily { public *; }

-keep,allowoptimization class me.rerere.asr.local.SherpaSttRuntime$OnlineSession { public *; }
