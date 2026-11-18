.class public final Lorg/webrtc/CameraXSession;
.super Ljava/lang/Object;
.source "CameraXSession.kt"

# interfaces
.implements Lorg/webrtc/CameraSession;


# annotations
.annotation build Landroidx/camera/camera2/interop/n;
.end annotation

.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lorg/webrtc/CameraXSession$CameraDeviceId;,
        Lorg/webrtc/CameraXSession$Companion;,
        Lorg/webrtc/CameraXSession$SessionState;,
        Lorg/webrtc/CameraXSession$StabilizationMode;,
        Lorg/webrtc/CameraXSession$WhenMappings;
    }
.end annotation

.annotation system Ldalvik/annotation/SourceDebugExtension;
    value = "SMAP\nCameraXSession.kt\nKotlin\n*S Kotlin\n*F\n+ 1 CameraXSession.kt\norg/webrtc/CameraXSession\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,396:1\n1557#2:397\n1628#2,3:398\n230#2,2:401\n774#2:404\n865#2,2:405\n1#3:403\n*S KotlinDebug\n*F\n+ 1 CameraXSession.kt\norg/webrtc/CameraXSession\n*L\n299#1:397\n299#1:398,3\n300#1:401,2\n157#1:404\n157#1:405,2\n*E\n"
.end annotation

.annotation runtime Lkotlin/Metadata;
    d1 = {
        "\u0000\u00c4\u0001\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u0008\n\u0002\u0008\u0003\n\u0002\u0010\u0011\n\u0002\u0018\u0002\n\u0002\u0008\u0003\n\u0002\u0010\u0002\n\u0002\u0008\u0004\n\u0002\u0018\u0002\n\u0002\u0008\u0007\n\u0002\u0018\u0002\n\u0002\u0008\u0005\n\u0002\u0018\u0002\n\u0002\u0008\u0002\n\u0002\u0018\u0002\n\u0002\u0008\u0005\n\u0002\u0010\u000b\n\u0002\u0008\n\n\u0002\u0018\u0002\n\u0002\u0008\u0002\n\u0002\u0018\u0002\n\u0002\u0008\u0002\n\u0002\u0018\u0002\n\u0002\u0008\u0002\n\u0002\u0018\u0002\n\u0002\u0008\u0002\n\u0002\u0018\u0002\n\u0002\u0008\u0004\n\u0002\u0018\u0002\n\u0002\u0008\u0005\n\u0002\u0010\t\n\u0002\u0008\u0003\n\u0002\u0018\u0002\n\u0002\u0008\u0002\n\u0002\u0018\u0002\n\u0002\u0008\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0008\n\u0008\u0007\u0018\u0000 f2\u00020\u0001:\u0004gfhiBc\u0008\u0000\u0012\u0006\u0010\u0003\u001a\u00020\u0002\u0012\u0006\u0010\u0005\u001a\u00020\u0004\u0012\u0006\u0010\u0007\u001a\u00020\u0006\u0012\u0006\u0010\t\u001a\u00020\u0008\u0012\u0006\u0010\u000b\u001a\u00020\n\u0012\u0006\u0010\r\u001a\u00020\u000c\u0012\u0006\u0010\u000f\u001a\u00020\u000e\u0012\u0006\u0010\u0010\u001a\u00020\u000e\u0012\u0006\u0010\u0011\u001a\u00020\u000e\u0012\u0010\u0008\u0002\u0010\u0014\u001a\n\u0012\u0006\u0008\u0001\u0012\u00020\u00130\u0012\u00a2\u0006\u0004\u0008\u0015\u0010\u0016J\u000f\u0010\u0018\u001a\u00020\u0017H\u0002\u00a2\u0006\u0004\u0008\u0018\u0010\u0019J\u000f\u0010\u001a\u001a\u00020\u0017H\u0002\u00a2\u0006\u0004\u0008\u001a\u0010\u0019J%\u0010\u001d\u001a\u0008\u0012\u0004\u0012\u00028\u00000\u001c\"\u0004\u0008\u0000\u0010\u001b*\u0008\u0012\u0004\u0012\u00028\u00000\u001cH\u0002\u00a2\u0006\u0004\u0008\u001d\u0010\u001eJ\u000f\u0010\u001f\u001a\u00020\u0017H\u0002\u00a2\u0006\u0004\u0008\u001f\u0010\u0019J\u0017\u0010!\u001a\u00020\u00172\u0006\u0010 \u001a\u00020\u000cH\u0002\u00a2\u0006\u0004\u0008!\u0010\"J\u000f\u0010#\u001a\u00020\u0017H\u0002\u00a2\u0006\u0004\u0008#\u0010\u0019J\u0017\u0010&\u001a\u00020\u00172\u0006\u0010%\u001a\u00020$H\u0002\u00a2\u0006\u0004\u0008&\u0010\'J\u0017\u0010(\u001a\u00020\u00172\u0006\u0010%\u001a\u00020$H\u0002\u00a2\u0006\u0004\u0008(\u0010\'J\u000f\u0010)\u001a\u00020\u0017H\u0002\u00a2\u0006\u0004\u0008)\u0010\u0019J!\u0010.\u001a\u0004\u0018\u00010-2\u0006\u0010+\u001a\u00020*2\u0006\u0010,\u001a\u00020\u000cH\u0002\u00a2\u0006\u0004\u0008.\u0010/J\u000f\u00100\u001a\u00020\u0017H\u0016\u00a2\u0006\u0004\u00080\u0010\u0019J\r\u00101\u001a\u00020\u000e\u00a2\u0006\u0004\u00081\u00102J\r\u00104\u001a\u000203\u00a2\u0006\u0004\u00084\u00105R\u0014\u0010\u0003\u001a\u00020\u00028\u0002X\u0082\u0004\u00a2\u0006\u0006\n\u0004\u0008\u0003\u00106R\u0014\u0010\u0005\u001a\u00020\u00048\u0002X\u0082\u0004\u00a2\u0006\u0006\n\u0004\u0008\u0005\u00107R\u0014\u0010\u0007\u001a\u00020\u00068\u0002X\u0082\u0004\u00a2\u0006\u0006\n\u0004\u0008\u0007\u00108R\u0014\u0010\t\u001a\u00020\u00088\u0002X\u0082\u0004\u00a2\u0006\u0006\n\u0004\u0008\t\u00109R\u0014\u0010\u000b\u001a\u00020\n8\u0002X\u0082\u0004\u00a2\u0006\u0006\n\u0004\u0008\u000b\u0010:R\u0014\u0010\r\u001a\u00020\u000c8\u0002X\u0082\u0004\u00a2\u0006\u0006\n\u0004\u0008\r\u0010;R\u0014\u0010\u000f\u001a\u00020\u000e8\u0002X\u0082\u0004\u00a2\u0006\u0006\n\u0004\u0008\u000f\u0010<R\u0014\u0010\u0010\u001a\u00020\u000e8\u0002X\u0082\u0004\u00a2\u0006\u0006\n\u0004\u0008\u0010\u0010<R\u0014\u0010\u0011\u001a\u00020\u000e8\u0002X\u0082\u0004\u00a2\u0006\u0006\n\u0004\u0008\u0011\u0010<R\u001c\u0010\u0014\u001a\n\u0012\u0006\u0008\u0001\u0012\u00020\u00130\u00128\u0002X\u0082\u0004\u00a2\u0006\u0006\n\u0004\u0008\u0014\u0010=R\u0016\u0010?\u001a\u00020>8\u0002@\u0002X\u0082\u000e\u00a2\u0006\u0006\n\u0004\u0008?\u0010@R\u0016\u0010B\u001a\u00020A8\u0002@\u0002X\u0082\u000e\u00a2\u0006\u0006\n\u0004\u0008B\u0010CR\u0016\u0010E\u001a\u00020D8\u0002@\u0002X\u0082.\u00a2\u0006\u0006\n\u0004\u0008E\u0010FR\u0016\u0010H\u001a\u00020G8\u0002@\u0002X\u0082.\u00a2\u0006\u0006\n\u0004\u0008H\u0010IR(\u0010%\u001a\u0004\u0018\u00010J2\u0008\u0010K\u001a\u0004\u0018\u00010J8\u0006@BX\u0086\u000e\u00a2\u0006\u000c\n\u0004\u0008%\u0010L\u001a\u0004\u0008M\u0010NR\u0018\u0010P\u001a\u0004\u0018\u00010O8\u0002@\u0002X\u0082\u000e\u00a2\u0006\u0006\n\u0004\u0008P\u0010QR\u0016\u0010R\u001a\u00020\u000e8\u0002@\u0002X\u0082\u000e\u00a2\u0006\u0006\n\u0004\u0008R\u0010<R\u0016\u00104\u001a\u0002038\u0002@\u0002X\u0082\u000e\u00a2\u0006\u0006\n\u0004\u00084\u0010SR\u0016\u0010T\u001a\u0002038\u0002@\u0002X\u0082\u000e\u00a2\u0006\u0006\n\u0004\u0008T\u0010SR\u0014\u0010V\u001a\u00020U8\u0002X\u0082\u0004\u00a2\u0006\u0006\n\u0004\u0008V\u0010WR\u0016\u0010X\u001a\u00020\u000e8\u0002@\u0002X\u0082\u000e\u00a2\u0006\u0006\n\u0004\u0008X\u0010<R\u0018\u0010Z\u001a\u0004\u0018\u00010Y8\u0002@\u0002X\u0082\u000e\u00a2\u0006\u0006\n\u0004\u0008Z\u0010[R\u0016\u0010]\u001a\u00020\\8\u0002@\u0002X\u0082\u000e\u00a2\u0006\u0006\n\u0004\u0008]\u0010^R\"\u0010a\u001a\u000e\u0012\u0004\u0012\u00020`\u0012\u0004\u0012\u00020\u00170_8\u0002@\u0002X\u0082\u000e\u00a2\u0006\u0006\n\u0004\u0008a\u0010bR\u0014\u0010e\u001a\u00020-8BX\u0082\u0004\u00a2\u0006\u0006\u001a\u0004\u0008c\u0010d\u00a8\u0006j"
    }
    d2 = {
        "Lorg/webrtc/CameraXSession;",
        "Lorg/webrtc/CameraSession;",
        "Lorg/webrtc/CameraSession$CreateSessionCallback;",
        "sessionCallback",
        "Lorg/webrtc/CameraSession$Events;",
        "events",
        "Landroid/content/Context;",
        "context",
        "Landroidx/lifecycle/o0;",
        "lifecycleOwner",
        "Lorg/webrtc/SurfaceTextureHelper;",
        "surfaceTextureHelper",
        "",
        "cameraId",
        "",
        "width",
        "height",
        "frameRate",
        "",
        "Landroidx/camera/core/a4;",
        "useCases",
        "<init>",
        "(Lorg/webrtc/CameraSession$CreateSessionCallback;Lorg/webrtc/CameraSession$Events;Landroid/content/Context;Landroidx/lifecycle/o0;Lorg/webrtc/SurfaceTextureHelper;Ljava/lang/String;III[Landroidx/camera/core/a4;)V",
        "",
        "start",
        "()V",
        "openCamera",
        "T",
        "Landroidx/camera/core/w0;",
        "applyCameraSettings",
        "(Landroidx/camera/core/w0;)Landroidx/camera/core/w0;",
        "stopInternal",
        "error",
        "reportError",
        "(Ljava/lang/String;)V",
        "obtainCameraConfiguration",
        "Landroidx/camera/camera2/interop/j;",
        "camera",
        "findCaptureFormat",
        "(Landroidx/camera/camera2/interop/j;)V",
        "findStabilizationMode",
        "checkIsOnCameraThread",
        "Landroid/hardware/camera2/CameraManager;",
        "cameraManager",
        "deviceId",
        "Lorg/webrtc/CameraXSession$CameraDeviceId;",
        "findCamera",
        "(Landroid/hardware/camera2/CameraManager;Ljava/lang/String;)Lorg/webrtc/CameraXSession$CameraDeviceId;",
        "stop",
        "getFrameOrientation",
        "()I",
        "",
        "isCameraFrontFacing",
        "()Z",
        "Lorg/webrtc/CameraSession$CreateSessionCallback;",
        "Lorg/webrtc/CameraSession$Events;",
        "Landroid/content/Context;",
        "Landroidx/lifecycle/o0;",
        "Lorg/webrtc/SurfaceTextureHelper;",
        "Ljava/lang/String;",
        "I",
        "[Landroidx/camera/core/a4;",
        "Lorg/webrtc/CameraXSession$SessionState;",
        "sessionState",
        "Lorg/webrtc/CameraXSession$SessionState;",
        "Landroid/os/Handler;",
        "cameraThreadHandler",
        "Landroid/os/Handler;",
        "Landroidx/camera/lifecycle/j;",
        "cameraProvider",
        "Landroidx/camera/lifecycle/j;",
        "Landroidx/camera/core/b3$c;",
        "surfaceProvider",
        "Landroidx/camera/core/b3$c;",
        "Landroidx/camera/core/p;",
        "value",
        "Landroidx/camera/core/p;",
        "getCamera",
        "()Landroidx/camera/core/p;",
        "Landroid/view/Surface;",
        "surface",
        "Landroid/view/Surface;",
        "cameraOrientation",
        "Z",
        "firstFrameReported",
        "",
        "constructionTimeNs",
        "J",
        "fpsUnitFactor",
        "Lorg/webrtc/CameraEnumerationAndroid$CaptureFormat;",
        "captureFormat",
        "Lorg/webrtc/CameraEnumerationAndroid$CaptureFormat;",
        "Lorg/webrtc/CameraXSession$StabilizationMode;",
        "stabilizationMode",
        "Lorg/webrtc/CameraXSession$StabilizationMode;",
        "Lkotlin/Function1;",
        "Lorg/webrtc/VideoFrame;",
        "surfaceTextureListener",
        "Lkotlin/jvm/functions/Function1;",
        "getCameraDevice",
        "()Lorg/webrtc/CameraXSession$CameraDeviceId;",
        "cameraDevice",
        "Companion",
        "CameraDeviceId",
        "SessionState",
        "StabilizationMode",
        "libjingle_peerconnection_release"
    }
    k = 0x1
    mv = {
        0x2,
        0x1,
        0x0
    }
    xi = 0x30
.end annotation

.annotation build Lkotlin/jvm/internal/SourceDebugExtension;
    value = {
        "SMAP\nCameraXSession.kt\nKotlin\n*S Kotlin\n*F\n+ 1 CameraXSession.kt\norg/webrtc/CameraXSession\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,396:1\n1557#2:397\n1628#2,3:398\n230#2,2:401\n774#2:404\n865#2,2:405\n1#3:403\n*S KotlinDebug\n*F\n+ 1 CameraXSession.kt\norg/webrtc/CameraXSession\n*L\n299#1:397\n299#1:398,3\n300#1:401,2\n157#1:404\n157#1:405,2\n*E\n"
    }
.end annotation


# static fields
.field public static final Companion:Lorg/webrtc/CameraXSession$Companion;
    .annotation build Lod/l;
    .end annotation
.end field

.field private static final cameraXResolutionHistogram:Lorg/webrtc/Histogram;

.field private static final cameraXStartTimeMsHistogram:Lorg/webrtc/Histogram;

.field private static final cameraXStopTimeMsHistogram:Lorg/webrtc/Histogram;

.field private static final logger:Lorg/apache/log4j/Logger;


# instance fields
.field private camera:Landroidx/camera/core/p;
    .annotation build Lod/m;
    .end annotation
.end field

.field private final cameraId:Ljava/lang/String;
    .annotation build Lod/l;
    .end annotation
.end field

.field private cameraOrientation:I

.field private cameraProvider:Landroidx/camera/lifecycle/j;

.field private cameraThreadHandler:Landroid/os/Handler;
    .annotation build Lod/l;
    .end annotation
.end field

.field private captureFormat:Lorg/webrtc/CameraEnumerationAndroid$CaptureFormat;
    .annotation build Lod/m;
    .end annotation
.end field

.field private final constructionTimeNs:J

.field private final context:Landroid/content/Context;
    .annotation build Lod/l;
    .end annotation
.end field

.field private final events:Lorg/webrtc/CameraSession$Events;
    .annotation build Lod/l;
    .end annotation
.end field

.field private firstFrameReported:Z

.field private fpsUnitFactor:I

.field private final frameRate:I

.field private final height:I

.field private isCameraFrontFacing:Z

.field private final lifecycleOwner:Landroidx/lifecycle/o0;
    .annotation build Lod/l;
    .end annotation
.end field

.field private final sessionCallback:Lorg/webrtc/CameraSession$CreateSessionCallback;
    .annotation build Lod/l;
    .end annotation
.end field

.field private sessionState:Lorg/webrtc/CameraXSession$SessionState;
    .annotation build Lod/l;
    .end annotation
.end field

.field private stabilizationMode:Lorg/webrtc/CameraXSession$StabilizationMode;
    .annotation build Lod/l;
    .end annotation
.end field

.field private surface:Landroid/view/Surface;
    .annotation build Lod/m;
    .end annotation
.end field

.field private surfaceProvider:Landroidx/camera/core/b3$c;

.field private final surfaceTextureHelper:Lorg/webrtc/SurfaceTextureHelper;
    .annotation build Lod/l;
    .end annotation
.end field

.field private surfaceTextureListener:Lkotlin/jvm/functions/Function1;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Lkotlin/jvm/functions/Function1<",
            "-",
            "Lorg/webrtc/VideoFrame;",
            "Lkotlin/Unit;",
            ">;"
        }
    .end annotation

    .annotation build Lod/l;
    .end annotation
.end field

.field private final useCases:[Landroidx/camera/core/a4;
    .annotation build Lod/l;
    .end annotation
.end field

.field private final width:I


# direct methods
.method static constructor <clinit>()V
    .locals 4

    .prologue
    .line 1
    new-instance v0, Lorg/webrtc/CameraXSession$Companion;

    .line 3
    const/4 v1, 0x0

    .line 4
    invoke-direct {v0, v1}, Lorg/webrtc/CameraXSession$Companion;-><init>(Lkotlin/jvm/internal/DefaultConstructorMarker;)V

    .line 7
    sput-object v0, Lorg/webrtc/CameraXSession;->Companion:Lorg/webrtc/CameraXSession$Companion;

    .line 9
    const-string v0, "CameraXSession"

    .line 11
    invoke-static {v0}, Lorg/apache/log4j/Logger;->getLogger(Ljava/lang/String;)Lorg/apache/log4j/Logger;

    .line 14
    move-result-object v0

    .line 15
    sput-object v0, Lorg/webrtc/CameraXSession;->logger:Lorg/apache/log4j/Logger;

    .line 17
    const-string v0, "WebRTC.Android.CameraX.StartTimeMs"

    .line 19
    const/4 v1, 0x1

    .line 20
    const/16 v2, 0x2710

    .line 22
    const/16 v3, 0x32

    .line 24
    invoke-static {v0, v1, v2, v3}, Lorg/webrtc/Histogram;->createCounts(Ljava/lang/String;III)Lorg/webrtc/Histogram;

    .line 27
    move-result-object v0

    .line 28
    sput-object v0, Lorg/webrtc/CameraXSession;->cameraXStartTimeMsHistogram:Lorg/webrtc/Histogram;

    .line 30
    const-string v0, "WebRTC.Android.CameraX.StopTimeMs"

    .line 32
    invoke-static {v0, v1, v2, v3}, Lorg/webrtc/Histogram;->createCounts(Ljava/lang/String;III)Lorg/webrtc/Histogram;

    .line 35
    move-result-object v0

    .line 36
    sput-object v0, Lorg/webrtc/CameraXSession;->cameraXStopTimeMsHistogram:Lorg/webrtc/Histogram;

    .line 38
    sget-object v0, Lorg/webrtc/CameraEnumerationAndroid;->COMMON_RESOLUTIONS:Ljava/util/ArrayList;

    .line 40
    invoke-virtual {v0}, Ljava/util/ArrayList;->size()I

    .line 43
    move-result v0

    .line 44
    const-string v1, "WebRTC.Android.CameraX.Resolution"

    .line 46
    invoke-static {v1, v0}, Lorg/webrtc/Histogram;->createEnumeration(Ljava/lang/String;I)Lorg/webrtc/Histogram;

    .line 49
    move-result-object v0

    .line 50
    sput-object v0, Lorg/webrtc/CameraXSession;->cameraXResolutionHistogram:Lorg/webrtc/Histogram;

    .line 52
    return-void
.end method

.method public constructor <init>(Lorg/webrtc/CameraSession$CreateSessionCallback;Lorg/webrtc/CameraSession$Events;Landroid/content/Context;Landroidx/lifecycle/o0;Lorg/webrtc/SurfaceTextureHelper;Ljava/lang/String;III[Landroidx/camera/core/a4;)V
    .locals 1
    .param p1    # Lorg/webrtc/CameraSession$CreateSessionCallback;
        .annotation build Lod/l;
        .end annotation
    .end param
    .param p2    # Lorg/webrtc/CameraSession$Events;
        .annotation build Lod/l;
        .end annotation
    .end param
    .param p3    # Landroid/content/Context;
        .annotation build Lod/l;
        .end annotation
    .end param
    .param p4    # Landroidx/lifecycle/o0;
        .annotation build Lod/l;
        .end annotation
    .end param
    .param p5    # Lorg/webrtc/SurfaceTextureHelper;
        .annotation build Lod/l;
        .end annotation
    .end param
    .param p6    # Ljava/lang/String;
        .annotation build Lod/l;
        .end annotation
    .end param
    .param p10    # [Landroidx/camera/core/a4;
        .annotation build Lod/l;
        .end annotation
    .end param

    .prologue
    const-string v0, "sessionCallback"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/Intrinsics;->p(Ljava/lang/Object;Ljava/lang/String;)V

    const-string v0, "events"

    invoke-static {p2, v0}, Lkotlin/jvm/internal/Intrinsics;->p(Ljava/lang/Object;Ljava/lang/String;)V

    const-string v0, "context"

    invoke-static {p3, v0}, Lkotlin/jvm/internal/Intrinsics;->p(Ljava/lang/Object;Ljava/lang/String;)V

    const-string v0, "lifecycleOwner"

    invoke-static {p4, v0}, Lkotlin/jvm/internal/Intrinsics;->p(Ljava/lang/Object;Ljava/lang/String;)V

    const-string v0, "surfaceTextureHelper"

    invoke-static {p5, v0}, Lkotlin/jvm/internal/Intrinsics;->p(Ljava/lang/Object;Ljava/lang/String;)V

    const-string v0, "cameraId"

    invoke-static {p6, v0}, Lkotlin/jvm/internal/Intrinsics;->p(Ljava/lang/Object;Ljava/lang/String;)V

    const-string v0, "useCases"

    invoke-static {p10, v0}, Lkotlin/jvm/internal/Intrinsics;->p(Ljava/lang/Object;Ljava/lang/String;)V

    .line 1
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    .line 2
    iput-object p1, p0, Lorg/webrtc/CameraXSession;->sessionCallback:Lorg/webrtc/CameraSession$CreateSessionCallback;

    .line 3
    iput-object p2, p0, Lorg/webrtc/CameraXSession;->events:Lorg/webrtc/CameraSession$Events;

    .line 4
    iput-object p3, p0, Lorg/webrtc/CameraXSession;->context:Landroid/content/Context;

    .line 5
    iput-object p4, p0, Lorg/webrtc/CameraXSession;->lifecycleOwner:Landroidx/lifecycle/o0;

    .line 6
    iput-object p5, p0, Lorg/webrtc/CameraXSession;->surfaceTextureHelper:Lorg/webrtc/SurfaceTextureHelper;

    .line 7
    iput-object p6, p0, Lorg/webrtc/CameraXSession;->cameraId:Ljava/lang/String;

    .line 8
    iput p7, p0, Lorg/webrtc/CameraXSession;->width:I

    .line 9
    iput p8, p0, Lorg/webrtc/CameraXSession;->height:I

    .line 10
    iput p9, p0, Lorg/webrtc/CameraXSession;->frameRate:I

    .line 11
    iput-object p10, p0, Lorg/webrtc/CameraXSession;->useCases:[Landroidx/camera/core/a4;

    .line 12
    sget-object p1, Lorg/webrtc/CameraXSession$SessionState;->RUNNING:Lorg/webrtc/CameraXSession$SessionState;

    iput-object p1, p0, Lorg/webrtc/CameraXSession;->sessionState:Lorg/webrtc/CameraXSession$SessionState;

    .line 13
    invoke-virtual {p5}, Lorg/webrtc/SurfaceTextureHelper;->getHandler()Landroid/os/Handler;

    move-result-object p1

    const-string p2, "getHandler(...)"

    invoke-static {p1, p2}, Lkotlin/jvm/internal/Intrinsics;->o(Ljava/lang/Object;Ljava/lang/String;)V

    iput-object p1, p0, Lorg/webrtc/CameraXSession;->cameraThreadHandler:Landroid/os/Handler;

    const/4 p1, 0x1

    .line 14
    iput-boolean p1, p0, Lorg/webrtc/CameraXSession;->isCameraFrontFacing:Z

    .line 15
    invoke-static {}, Ljava/lang/System;->nanoTime()J

    move-result-wide p2

    iput-wide p2, p0, Lorg/webrtc/CameraXSession;->constructionTimeNs:J

    .line 16
    iput p1, p0, Lorg/webrtc/CameraXSession;->fpsUnitFactor:I

    .line 17
    sget-object p1, Lorg/webrtc/CameraXSession$StabilizationMode;->NONE:Lorg/webrtc/CameraXSession$StabilizationMode;

    iput-object p1, p0, Lorg/webrtc/CameraXSession;->stabilizationMode:Lorg/webrtc/CameraXSession$StabilizationMode;

    .line 18
    new-instance p1, Lorg/webrtc/p;

    invoke-direct {p1, p0}, Lorg/webrtc/p;-><init>(Lorg/webrtc/CameraXSession;)V

    iput-object p1, p0, Lorg/webrtc/CameraXSession;->surfaceTextureListener:Lkotlin/jvm/functions/Function1;

    .line 19
    iget-object p1, p0, Lorg/webrtc/CameraXSession;->cameraThreadHandler:Landroid/os/Handler;

    new-instance p2, Lorg/webrtc/q;

    invoke-direct {p2, p0}, Lorg/webrtc/q;-><init>(Lorg/webrtc/CameraXSession;)V

    invoke-virtual {p1, p2}, Landroid/os/Handler;->post(Ljava/lang/Runnable;)Z

    return-void
.end method

.method public synthetic constructor <init>(Lorg/webrtc/CameraSession$CreateSessionCallback;Lorg/webrtc/CameraSession$Events;Landroid/content/Context;Landroidx/lifecycle/o0;Lorg/webrtc/SurfaceTextureHelper;Ljava/lang/String;III[Landroidx/camera/core/a4;ILkotlin/jvm/internal/DefaultConstructorMarker;)V
    .locals 12

    .prologue
    move/from16 v0, p11

    and-int/lit16 v0, v0, 0x200

    if-eqz v0, :cond_0

    const/4 v0, 0x0

    .line 20
    new-array v0, v0, [Landroidx/camera/core/a4;

    move-object v11, v0

    :goto_0
    move-object v1, p0

    move-object v2, p1

    move-object v3, p2

    move-object v4, p3

    move-object/from16 v5, p4

    move-object/from16 v6, p5

    move-object/from16 v7, p6

    move/from16 v8, p7

    move/from16 v9, p8

    move/from16 v10, p9

    goto :goto_1

    :cond_0
    move-object/from16 v11, p10

    goto :goto_0

    .line 21
    :goto_1
    invoke-direct/range {v1 .. v11}, Lorg/webrtc/CameraXSession;-><init>(Lorg/webrtc/CameraSession$CreateSessionCallback;Lorg/webrtc/CameraSession$Events;Landroid/content/Context;Landroidx/lifecycle/o0;Lorg/webrtc/SurfaceTextureHelper;Ljava/lang/String;III[Landroidx/camera/core/a4;)V

    return-void
.end method

.method private static final _init_$lambda$1(Lorg/webrtc/CameraXSession;)V
    .locals 0

    .prologue
    .line 1
    invoke-direct {p0}, Lorg/webrtc/CameraXSession;->start()V

    .line 4
    return-void
.end method

.method public static synthetic a(Lkotlin/jvm/functions/Function1;Lorg/webrtc/VideoFrame;)V
    .locals 0

    .prologue
    .line 1
    invoke-static {p0, p1}, Lorg/webrtc/CameraXSession;->start$lambda$2(Lkotlin/jvm/functions/Function1;Lorg/webrtc/VideoFrame;)V

    .line 4
    return-void
.end method

.method private final applyCameraSettings(Landroidx/camera/core/w0;)Landroidx/camera/core/w0;
    .locals 6
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "<T:",
            "Ljava/lang/Object;",
            ">(",
            "Landroidx/camera/core/w0<",
            "TT;>;)",
            "Landroidx/camera/core/w0<",
            "TT;>;"
        }
    .end annotation

    .prologue
    .line 1
    new-instance v0, Landroidx/camera/camera2/interop/k$a;

    .line 3
    invoke-direct {v0, p1}, Landroidx/camera/camera2/interop/k$a;-><init>(Landroidx/camera/core/w0;)V

    .line 6
    invoke-direct {p0}, Lorg/webrtc/CameraXSession;->getCameraDevice()Lorg/webrtc/CameraXSession$CameraDeviceId;

    .line 9
    move-result-object v1

    .line 10
    invoke-virtual {v1}, Lorg/webrtc/CameraXSession$CameraDeviceId;->getPhysicalId()Ljava/lang/String;

    .line 13
    move-result-object v1

    .line 14
    if-eqz v1, :cond_0

    .line 16
    sget v2, Landroid/os/Build$VERSION;->SDK_INT:I

    .line 18
    const/16 v3, 0x1c

    .line 20
    if-lt v2, v3, :cond_0

    .line 22
    invoke-virtual {v0, v1}, Landroidx/camera/camera2/interop/k$a;->d(Ljava/lang/String;)Landroidx/camera/camera2/interop/k$a;

    .line 25
    :cond_0
    iget-object v1, p0, Lorg/webrtc/CameraXSession;->captureFormat:Lorg/webrtc/CameraEnumerationAndroid$CaptureFormat;

    .line 27
    if-nez v1, :cond_1

    .line 29
    goto :goto_0

    .line 30
    :cond_1
    sget-object v2, Landroid/hardware/camera2/CaptureRequest;->CONTROL_AE_TARGET_FPS_RANGE:Landroid/hardware/camera2/CaptureRequest$Key;

    .line 32
    new-instance v3, Landroid/util/Range;

    .line 34
    iget-object v4, v1, Lorg/webrtc/CameraEnumerationAndroid$CaptureFormat;->framerate:Lorg/webrtc/CameraEnumerationAndroid$CaptureFormat$FramerateRange;

    .line 36
    iget v4, v4, Lorg/webrtc/CameraEnumerationAndroid$CaptureFormat$FramerateRange;->min:I

    .line 38
    iget v5, p0, Lorg/webrtc/CameraXSession;->fpsUnitFactor:I

    .line 40
    div-int/2addr v4, v5

    .line 41
    invoke-static {v4}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    .line 44
    move-result-object v4

    .line 45
    iget-object v1, v1, Lorg/webrtc/CameraEnumerationAndroid$CaptureFormat;->framerate:Lorg/webrtc/CameraEnumerationAndroid$CaptureFormat$FramerateRange;

    .line 47
    iget v1, v1, Lorg/webrtc/CameraEnumerationAndroid$CaptureFormat$FramerateRange;->max:I

    .line 49
    iget v5, p0, Lorg/webrtc/CameraXSession;->fpsUnitFactor:I

    .line 51
    div-int/2addr v1, v5

    .line 52
    invoke-static {v1}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    .line 55
    move-result-object v1

    .line 56
    invoke-direct {v3, v4, v1}, Landroid/util/Range;-><init>(Ljava/lang/Comparable;Ljava/lang/Comparable;)V

    .line 59
    invoke-virtual {v0, v2, v3}, Landroidx/camera/camera2/interop/k$a;->a(Landroid/hardware/camera2/CaptureRequest$Key;Ljava/lang/Object;)Landroidx/camera/camera2/interop/k$a;

    .line 62
    sget-object v1, Landroid/hardware/camera2/CaptureRequest;->CONTROL_AE_MODE:Landroid/hardware/camera2/CaptureRequest$Key;

    .line 64
    const/4 v2, 0x1

    .line 65
    invoke-static {v2}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    .line 68
    move-result-object v3

    .line 69
    invoke-virtual {v0, v1, v3}, Landroidx/camera/camera2/interop/k$a;->a(Landroid/hardware/camera2/CaptureRequest$Key;Ljava/lang/Object;)Landroidx/camera/camera2/interop/k$a;

    .line 72
    sget-object v1, Landroid/hardware/camera2/CaptureRequest;->CONTROL_AE_LOCK:Landroid/hardware/camera2/CaptureRequest$Key;

    .line 74
    sget-object v3, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;

    .line 76
    invoke-virtual {v0, v1, v3}, Landroidx/camera/camera2/interop/k$a;->a(Landroid/hardware/camera2/CaptureRequest$Key;Ljava/lang/Object;)Landroidx/camera/camera2/interop/k$a;

    .line 79
    iget-object v1, p0, Lorg/webrtc/CameraXSession;->stabilizationMode:Lorg/webrtc/CameraXSession$StabilizationMode;

    .line 81
    sget-object v3, Lorg/webrtc/CameraXSession$WhenMappings;->$EnumSwitchMapping$0:[I

    .line 83
    invoke-virtual {v1}, Ljava/lang/Enum;->ordinal()I

    .line 86
    move-result v1

    .line 87
    aget v1, v3, v1

    .line 89
    const/4 v3, 0x0

    .line 90
    if-eq v1, v2, :cond_3

    .line 92
    const/4 v4, 0x2

    .line 93
    if-eq v1, v4, :cond_2

    .line 95
    :goto_0
    return-object p1

    .line 96
    :cond_2
    sget-object v1, Landroid/hardware/camera2/CaptureRequest;->CONTROL_VIDEO_STABILIZATION_MODE:Landroid/hardware/camera2/CaptureRequest$Key;

    .line 98
    invoke-static {v2}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    .line 101
    move-result-object v2

    .line 102
    invoke-virtual {v0, v1, v2}, Landroidx/camera/camera2/interop/k$a;->a(Landroid/hardware/camera2/CaptureRequest$Key;Ljava/lang/Object;)Landroidx/camera/camera2/interop/k$a;

    .line 105
    sget-object v1, Landroid/hardware/camera2/CaptureRequest;->LENS_OPTICAL_STABILIZATION_MODE:Landroid/hardware/camera2/CaptureRequest$Key;

    .line 107
    invoke-static {v3}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    .line 110
    move-result-object v2

    .line 111
    invoke-virtual {v0, v1, v2}, Landroidx/camera/camera2/interop/k$a;->a(Landroid/hardware/camera2/CaptureRequest$Key;Ljava/lang/Object;)Landroidx/camera/camera2/interop/k$a;

    .line 114
    return-object p1

    .line 115
    :cond_3
    sget-object v1, Landroid/hardware/camera2/CaptureRequest;->LENS_OPTICAL_STABILIZATION_MODE:Landroid/hardware/camera2/CaptureRequest$Key;

    .line 117
    invoke-static {v2}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    .line 120
    move-result-object v2

    .line 121
    invoke-virtual {v0, v1, v2}, Landroidx/camera/camera2/interop/k$a;->a(Landroid/hardware/camera2/CaptureRequest$Key;Ljava/lang/Object;)Landroidx/camera/camera2/interop/k$a;

    .line 124
    sget-object v1, Landroid/hardware/camera2/CaptureRequest;->CONTROL_VIDEO_STABILIZATION_MODE:Landroid/hardware/camera2/CaptureRequest$Key;

    .line 126
    invoke-static {v3}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    .line 129
    move-result-object v2

    .line 130
    invoke-virtual {v0, v1, v2}, Landroidx/camera/camera2/interop/k$a;->a(Landroid/hardware/camera2/CaptureRequest$Key;Ljava/lang/Object;)Landroidx/camera/camera2/interop/k$a;

    .line 133
    return-object p1
.end method

.method public static synthetic b(Lorg/webrtc/CameraXSession;Landroidx/camera/core/y;)Lkotlin/Unit;
    .locals 0

    .prologue
    .line 1
    invoke-static {p0, p1}, Lorg/webrtc/CameraXSession;->openCamera$lambda$17$lambda$16$lambda$14(Lorg/webrtc/CameraXSession;Landroidx/camera/core/y;)Lkotlin/Unit;

    .line 4
    move-result-object p0

    .line 5
    return-object p0
.end method

.method public static synthetic c(Landroidx/camera/core/y3$g;)V
    .locals 0

    .prologue
    .line 1
    invoke-static {p0}, Lorg/webrtc/CameraXSession;->openCamera$lambda$17$lambda$7$lambda$6$lambda$5(Landroidx/camera/core/y3$g;)V

    .line 4
    return-void
.end method

.method private final checkIsOnCameraThread()V
    .locals 2

    .prologue
    .line 1
    invoke-static {}, Ljava/lang/Thread;->currentThread()Ljava/lang/Thread;

    .line 4
    move-result-object v0

    .line 5
    iget-object v1, p0, Lorg/webrtc/CameraXSession;->cameraThreadHandler:Landroid/os/Handler;

    .line 7
    invoke-virtual {v1}, Landroid/os/Handler;->getLooper()Landroid/os/Looper;

    .line 10
    move-result-object v1

    .line 11
    invoke-virtual {v1}, Landroid/os/Looper;->getThread()Ljava/lang/Thread;

    .line 14
    move-result-object v1

    .line 15
    if-ne v0, v1, :cond_0

    .line 17
    return-void

    .line 18
    :cond_0
    new-instance v0, Ljava/lang/IllegalStateException;

    .line 20
    const-string v1, "Wrong thread"

    .line 22
    invoke-direct {v0, v1}, Ljava/lang/IllegalStateException;-><init>(Ljava/lang/String;)V

    .line 25
    throw v0
.end method

.method public static synthetic d(Lorg/webrtc/CameraXSession;)V
    .locals 0

    .prologue
    .line 1
    invoke-static {p0}, Lorg/webrtc/CameraXSession;->_init_$lambda$1(Lorg/webrtc/CameraXSession;)V

    .line 4
    return-void
.end method

.method public static synthetic e(Lorg/webrtc/CameraXSession;)V
    .locals 0

    .prologue
    .line 1
    invoke-static {p0}, Lorg/webrtc/CameraXSession;->openCamera$lambda$17$lambda$16$lambda$11(Lorg/webrtc/CameraXSession;)V

    .line 4
    return-void
.end method

.method public static synthetic f(Lorg/webrtc/CameraXSession;)V
    .locals 0

    .prologue
    .line 1
    invoke-static {p0}, Lorg/webrtc/CameraXSession;->stopInternal$lambda$19(Lorg/webrtc/CameraXSession;)V

    .line 4
    return-void
.end method

.method private final findCamera(Landroid/hardware/camera2/CameraManager;Ljava/lang/String;)Lorg/webrtc/CameraXSession$CameraDeviceId;
    .locals 7

    .prologue
    .line 1
    invoke-virtual {p1}, Landroid/hardware/camera2/CameraManager;->getCameraIdList()[Ljava/lang/String;

    .line 4
    move-result-object v0

    .line 5
    array-length v1, v0

    .line 6
    const/4 v2, 0x0

    .line 7
    :goto_0
    const/4 v3, 0x0

    .line 8
    if-ge v2, v1, :cond_3

    .line 10
    aget-object v4, v0, v2

    .line 12
    invoke-static {v4, p2}, Lkotlin/jvm/internal/Intrinsics;->g(Ljava/lang/Object;Ljava/lang/Object;)Z

    .line 15
    move-result v5

    .line 16
    if-eqz v5, :cond_0

    .line 18
    new-instance p1, Lorg/webrtc/CameraXSession$CameraDeviceId;

    .line 20
    invoke-direct {p1, v4, v3}, Lorg/webrtc/CameraXSession$CameraDeviceId;-><init>(Ljava/lang/String;Ljava/lang/String;)V

    .line 23
    return-object p1

    .line 24
    :cond_0
    sget v3, Landroid/os/Build$VERSION;->SDK_INT:I

    .line 26
    const/16 v5, 0x1c

    .line 28
    if-lt v3, v5, :cond_2

    .line 30
    invoke-virtual {p1, v4}, Landroid/hardware/camera2/CameraManager;->getCameraCharacteristics(Ljava/lang/String;)Landroid/hardware/camera2/CameraCharacteristics;

    .line 33
    move-result-object v3

    .line 34
    const-string v5, "getCameraCharacteristics(...)"

    .line 36
    invoke-static {v3, v5}, Lkotlin/jvm/internal/Intrinsics;->o(Ljava/lang/Object;Ljava/lang/String;)V

    .line 39
    invoke-static {v3}, Lorg/webrtc/g;->a(Landroid/hardware/camera2/CameraCharacteristics;)Ljava/util/Set;

    .line 42
    move-result-object v3

    .line 43
    invoke-interface {v3}, Ljava/util/Set;->iterator()Ljava/util/Iterator;

    .line 46
    move-result-object v3

    .line 47
    :cond_1
    invoke-interface {v3}, Ljava/util/Iterator;->hasNext()Z

    .line 50
    move-result v5

    .line 51
    if-eqz v5, :cond_2

    .line 53
    invoke-interface {v3}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    .line 56
    move-result-object v5

    .line 57
    check-cast v5, Ljava/lang/String;

    .line 59
    invoke-static {p2, v5}, Lkotlin/jvm/internal/Intrinsics;->g(Ljava/lang/Object;Ljava/lang/Object;)Z

    .line 62
    move-result v6

    .line 63
    if-eqz v6, :cond_1

    .line 65
    new-instance p1, Lorg/webrtc/CameraXSession$CameraDeviceId;

    .line 67
    invoke-static {v4}, Lkotlin/jvm/internal/Intrinsics;->m(Ljava/lang/Object;)V

    .line 70
    invoke-direct {p1, v4, v5}, Lorg/webrtc/CameraXSession$CameraDeviceId;-><init>(Ljava/lang/String;Ljava/lang/String;)V

    .line 73
    return-object p1

    .line 74
    :cond_2
    add-int/lit8 v2, v2, 0x1

    .line 76
    goto :goto_0

    .line 77
    :cond_3
    return-object v3
.end method

.method private final findCaptureFormat(Landroidx/camera/camera2/interop/j;)V
    .locals 4

    .prologue
    .line 1
    sget-object v0, Landroid/hardware/camera2/CameraCharacteristics;->CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES:Landroid/hardware/camera2/CameraCharacteristics$Key;

    .line 3
    invoke-virtual {p1, v0}, Landroidx/camera/camera2/interop/j;->c(Landroid/hardware/camera2/CameraCharacteristics$Key;)Ljava/lang/Object;

    .line 6
    move-result-object v0

    .line 7
    check-cast v0, [Landroid/util/Range;

    .line 9
    invoke-static {v0}, Lorg/webrtc/Camera2Enumerator;->getFpsUnitFactor([Landroid/util/Range;)I

    .line 12
    move-result v1

    .line 13
    iput v1, p0, Lorg/webrtc/CameraXSession;->fpsUnitFactor:I

    .line 15
    invoke-static {v0, v1}, Lorg/webrtc/Camera2Enumerator;->convertFramerates([Landroid/util/Range;I)Ljava/util/List;

    .line 18
    move-result-object v0

    .line 19
    sget-object v1, Lorg/webrtc/CameraXEnumerator;->Companion:Lorg/webrtc/CameraXEnumerator$Companion;

    .line 21
    invoke-virtual {v1, p1}, Lorg/webrtc/CameraXEnumerator$Companion;->getSupportedSizes(Landroidx/camera/camera2/interop/j;)Ljava/util/List;

    .line 24
    move-result-object p1

    .line 25
    sget-object v1, Lorg/webrtc/CameraXSession;->logger:Lorg/apache/log4j/Logger;

    .line 27
    new-instance v2, Ljava/lang/StringBuilder;

    .line 29
    const-string v3, "Available preview sizes: "

    .line 31
    invoke-direct {v2, v3}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    .line 34
    invoke-virtual {v2, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    .line 37
    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    .line 40
    move-result-object v2

    .line 41
    invoke-virtual {v1, v2}, Lorg/apache/log4j/Category;->debug(Ljava/lang/Object;)V

    .line 44
    new-instance v2, Ljava/lang/StringBuilder;

    .line 46
    const-string v3, "Available fps ranges: "

    .line 48
    invoke-direct {v2, v3}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    .line 51
    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    .line 54
    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    .line 57
    move-result-object v2

    .line 58
    invoke-virtual {v1, v2}, Lorg/apache/log4j/Category;->debug(Ljava/lang/Object;)V

    .line 61
    invoke-interface {v0}, Ljava/util/List;->isEmpty()Z

    .line 64
    move-result v2

    .line 65
    if-nez v2, :cond_1

    .line 67
    invoke-interface {p1}, Ljava/util/List;->isEmpty()Z

    .line 70
    move-result v2

    .line 71
    if-eqz v2, :cond_0

    .line 73
    goto :goto_0

    .line 74
    :cond_0
    iget v2, p0, Lorg/webrtc/CameraXSession;->frameRate:I

    .line 76
    invoke-static {v0, v2}, Lorg/webrtc/CameraEnumerationAndroid;->getClosestSupportedFramerateRange(Ljava/util/List;I)Lorg/webrtc/CameraEnumerationAndroid$CaptureFormat$FramerateRange;

    .line 79
    move-result-object v0

    .line 80
    iget v2, p0, Lorg/webrtc/CameraXSession;->width:I

    .line 82
    iget v3, p0, Lorg/webrtc/CameraXSession;->height:I

    .line 84
    invoke-static {p1, v2, v3}, Lorg/webrtc/CameraEnumerationAndroid;->getClosestSupportedSize(Ljava/util/List;II)Lorg/webrtc/Size;

    .line 87
    move-result-object p1

    .line 88
    sget-object v2, Lorg/webrtc/CameraXSession;->cameraXResolutionHistogram:Lorg/webrtc/Histogram;

    .line 90
    invoke-static {v2, p1}, Lorg/webrtc/CameraEnumerationAndroid;->reportCameraResolution(Lorg/webrtc/Histogram;Lorg/webrtc/Size;)V

    .line 93
    new-instance v2, Lorg/webrtc/CameraEnumerationAndroid$CaptureFormat;

    .line 95
    iget v3, p1, Lorg/webrtc/Size;->width:I

    .line 97
    iget p1, p1, Lorg/webrtc/Size;->height:I

    .line 99
    invoke-direct {v2, v3, p1, v0}, Lorg/webrtc/CameraEnumerationAndroid$CaptureFormat;-><init>(IILorg/webrtc/CameraEnumerationAndroid$CaptureFormat$FramerateRange;)V

    .line 102
    iput-object v2, p0, Lorg/webrtc/CameraXSession;->captureFormat:Lorg/webrtc/CameraEnumerationAndroid$CaptureFormat;

    .line 104
    new-instance p1, Ljava/lang/StringBuilder;

    .line 106
    const-string v0, "Using capture format: "

    .line 108
    invoke-direct {p1, v0}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    .line 111
    invoke-virtual {p1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    .line 114
    invoke-virtual {p1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    .line 117
    move-result-object p1

    .line 118
    invoke-virtual {v1, p1}, Lorg/apache/log4j/Category;->debug(Ljava/lang/Object;)V

    .line 121
    return-void

    .line 122
    :cond_1
    :goto_0
    const-string p1, "No supported capture formats."

    .line 124
    invoke-direct {p0, p1}, Lorg/webrtc/CameraXSession;->reportError(Ljava/lang/String;)V

    .line 127
    return-void
.end method

.method private final findStabilizationMode(Landroidx/camera/camera2/interop/j;)V
    .locals 3

    .prologue
    .line 1
    sget-object v0, Landroid/hardware/camera2/CameraCharacteristics;->LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION:Landroid/hardware/camera2/CameraCharacteristics$Key;

    .line 3
    invoke-virtual {p1, v0}, Landroidx/camera/camera2/interop/j;->c(Landroid/hardware/camera2/CameraCharacteristics$Key;)Ljava/lang/Object;

    .line 6
    move-result-object v1

    .line 7
    check-cast v1, [I

    .line 9
    const/4 v2, 0x1

    .line 10
    if-eqz v1, :cond_0

    .line 12
    invoke-static {v1, v2}, Lkotlin/collections/ArraysKt;->z8([II)Z

    .line 15
    move-result v1

    .line 16
    if-ne v1, v2, :cond_0

    .line 18
    sget-object p1, Lorg/webrtc/CameraXSession$StabilizationMode;->OPTICAL:Lorg/webrtc/CameraXSession$StabilizationMode;

    .line 20
    iput-object p1, p0, Lorg/webrtc/CameraXSession;->stabilizationMode:Lorg/webrtc/CameraXSession$StabilizationMode;

    .line 22
    return-void

    .line 23
    :cond_0
    invoke-virtual {p1, v0}, Landroidx/camera/camera2/interop/j;->c(Landroid/hardware/camera2/CameraCharacteristics$Key;)Ljava/lang/Object;

    .line 26
    move-result-object p1

    .line 27
    check-cast p1, [I

    .line 29
    if-eqz p1, :cond_1

    .line 31
    invoke-static {p1, v2}, Lkotlin/collections/ArraysKt;->z8([II)Z

    .line 34
    move-result p1

    .line 35
    if-ne p1, v2, :cond_1

    .line 37
    sget-object p1, Lorg/webrtc/CameraXSession$StabilizationMode;->VIDEO:Lorg/webrtc/CameraXSession$StabilizationMode;

    .line 39
    iput-object p1, p0, Lorg/webrtc/CameraXSession;->stabilizationMode:Lorg/webrtc/CameraXSession$StabilizationMode;

    .line 41
    :cond_1
    return-void
.end method

.method public static synthetic g(Lorg/webrtc/CameraXSession;Lcom/google/common/util/concurrent/t1;Ljava/util/concurrent/Executor;)V
    .locals 0

    .prologue
    .line 1
    invoke-static {p0, p1, p2}, Lorg/webrtc/CameraXSession;->openCamera$lambda$17(Lorg/webrtc/CameraXSession;Lcom/google/common/util/concurrent/t1;Ljava/util/concurrent/Executor;)V

    .line 4
    return-void
.end method

.method private final getCameraDevice()Lorg/webrtc/CameraXSession$CameraDeviceId;
    .locals 4

    .prologue
    .line 1
    iget-object v0, p0, Lorg/webrtc/CameraXSession;->context:Landroid/content/Context;

    .line 3
    const-string v1, "camera"

    .line 5
    invoke-virtual {v0, v1}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    .line 8
    move-result-object v0

    .line 9
    const-string v1, "null cannot be cast to non-null type android.hardware.camera2.CameraManager"

    .line 11
    invoke-static {v0, v1}, Lkotlin/jvm/internal/Intrinsics;->n(Ljava/lang/Object;Ljava/lang/String;)V

    .line 14
    check-cast v0, Landroid/hardware/camera2/CameraManager;

    .line 16
    iget-object v1, p0, Lorg/webrtc/CameraXSession;->cameraId:Ljava/lang/String;

    .line 18
    invoke-direct {p0, v0, v1}, Lorg/webrtc/CameraXSession;->findCamera(Landroid/hardware/camera2/CameraManager;Ljava/lang/String;)Lorg/webrtc/CameraXSession$CameraDeviceId;

    .line 21
    move-result-object v0

    .line 22
    if-eqz v0, :cond_0

    .line 24
    return-object v0

    .line 25
    :cond_0
    new-instance v0, Ljava/lang/IllegalArgumentException;

    .line 27
    iget-object v1, p0, Lorg/webrtc/CameraXSession;->cameraId:Ljava/lang/String;

    .line 29
    const-string v2, "Camera ID "

    .line 31
    const-string v3, " not found"

    .line 33
    invoke-static {v2, v1, v3}, Landroid/support/v4/media/d;->a(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    .line 36
    move-result-object v1

    .line 37
    invoke-direct {v0, v1}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    .line 40
    throw v0
.end method

.method public static synthetic h(Lorg/webrtc/CameraXSession;Lorg/webrtc/VideoFrame;)Lkotlin/Unit;
    .locals 0

    .prologue
    .line 1
    invoke-static {p0, p1}, Lorg/webrtc/CameraXSession;->surfaceTextureListener$lambda$0(Lorg/webrtc/CameraXSession;Lorg/webrtc/VideoFrame;)Lkotlin/Unit;

    .line 4
    move-result-object p0

    .line 5
    return-object p0
.end method

.method public static synthetic i(Lorg/webrtc/CameraXSession;Landroidx/camera/core/x;)V
    .locals 0

    .prologue
    .line 1
    invoke-static {p0, p1}, Lorg/webrtc/CameraXSession;->openCamera$lambda$17$lambda$16(Lorg/webrtc/CameraXSession;Landroidx/camera/core/x;)V

    .line 4
    return-void
.end method

.method public static synthetic j(Lorg/webrtc/CameraXSession;Ljava/util/concurrent/Executor;Landroidx/camera/core/y3;)V
    .locals 0

    .prologue
    .line 1
    invoke-static {p0, p1, p2}, Lorg/webrtc/CameraXSession;->openCamera$lambda$17$lambda$7(Lorg/webrtc/CameraXSession;Ljava/util/concurrent/Executor;Landroidx/camera/core/y3;)V

    .line 4
    return-void
.end method

.method public static synthetic k(Lorg/webrtc/CameraXSession;Ljava/lang/Runnable;)V
    .locals 0

    .prologue
    .line 1
    invoke-static {p0, p1}, Lorg/webrtc/CameraXSession;->openCamera$lambda$4(Lorg/webrtc/CameraXSession;Ljava/lang/Runnable;)V

    .line 4
    return-void
.end method

.method public static synthetic l(Lorg/webrtc/CameraXSession;Ljava/util/List;)Ljava/util/List;
    .locals 0

    .prologue
    .line 1
    invoke-static {p0, p1}, Lorg/webrtc/CameraXSession;->openCamera$lambda$17$lambda$9(Lorg/webrtc/CameraXSession;Ljava/util/List;)Ljava/util/List;

    .line 4
    move-result-object p0

    .line 5
    return-object p0
.end method

.method public static synthetic m(Lorg/webrtc/CameraXSession;Landroidx/camera/core/y;)V
    .locals 0

    .prologue
    .line 1
    invoke-static {p0, p1}, Lorg/webrtc/CameraXSession;->openCamera$lambda$17$lambda$16$lambda$14$lambda$13(Lorg/webrtc/CameraXSession;Landroidx/camera/core/y;)V

    .line 4
    return-void
.end method

.method public static synthetic n(Lkotlin/jvm/functions/Function1;Ljava/lang/Object;)V
    .locals 0

    .prologue
    .line 1
    invoke-static {p0, p1}, Lorg/webrtc/CameraXSession;->openCamera$lambda$17$lambda$16$lambda$15(Lkotlin/jvm/functions/Function1;Ljava/lang/Object;)V

    .line 4
    return-void
.end method

.method private final obtainCameraConfiguration()V
    .locals 5

    .prologue
    .line 1
    iget-object v0, p0, Lorg/webrtc/CameraXSession;->cameraProvider:Landroidx/camera/lifecycle/j;

    .line 3
    if-eqz v0, :cond_7

    .line 5
    invoke-virtual {v0}, Landroidx/camera/lifecycle/j;->f()Ljava/util/List;

    .line 8
    move-result-object v0

    .line 9
    new-instance v1, Ljava/util/ArrayList;

    .line 11
    const/16 v2, 0xa

    .line 13
    invoke-static {v0, v2}, Lkotlin/collections/CollectionsKt;->b0(Ljava/lang/Iterable;I)I

    .line 16
    move-result v2

    .line 17
    invoke-direct {v1, v2}, Ljava/util/ArrayList;-><init>(I)V

    .line 20
    invoke-interface {v0}, Ljava/lang/Iterable;->iterator()Ljava/util/Iterator;

    .line 23
    move-result-object v0

    .line 24
    :goto_0
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    .line 27
    move-result v2

    .line 28
    if-eqz v2, :cond_0

    .line 30
    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    .line 33
    move-result-object v2

    .line 34
    check-cast v2, Landroidx/camera/core/v;

    .line 36
    invoke-static {v2}, Landroidx/camera/camera2/interop/j;->b(Landroidx/camera/core/v;)Landroidx/camera/camera2/interop/j;

    .line 39
    move-result-object v2

    .line 40
    invoke-interface {v1, v2}, Ljava/util/Collection;->add(Ljava/lang/Object;)Z

    .line 43
    goto :goto_0

    .line 44
    :cond_0
    invoke-interface {v1}, Ljava/lang/Iterable;->iterator()Ljava/util/Iterator;

    .line 47
    move-result-object v0

    .line 48
    :cond_1
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    .line 51
    move-result v1

    .line 52
    if-eqz v1, :cond_6

    .line 54
    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    .line 57
    move-result-object v1

    .line 58
    move-object v2, v1

    .line 59
    check-cast v2, Landroidx/camera/camera2/interop/j;

    .line 61
    invoke-virtual {v2}, Landroidx/camera/camera2/interop/j;->e()Ljava/lang/String;

    .line 64
    move-result-object v3

    .line 65
    invoke-direct {p0}, Lorg/webrtc/CameraXSession;->getCameraDevice()Lorg/webrtc/CameraXSession$CameraDeviceId;

    .line 68
    move-result-object v4

    .line 69
    invoke-virtual {v4}, Lorg/webrtc/CameraXSession$CameraDeviceId;->getDeviceId()Ljava/lang/String;

    .line 72
    move-result-object v4

    .line 73
    invoke-static {v3, v4}, Lkotlin/jvm/internal/Intrinsics;->g(Ljava/lang/Object;Ljava/lang/Object;)Z

    .line 76
    move-result v3

    .line 77
    if-nez v3, :cond_2

    .line 79
    invoke-virtual {v2}, Landroidx/camera/camera2/interop/j;->e()Ljava/lang/String;

    .line 82
    move-result-object v2

    .line 83
    invoke-direct {p0}, Lorg/webrtc/CameraXSession;->getCameraDevice()Lorg/webrtc/CameraXSession$CameraDeviceId;

    .line 86
    move-result-object v3

    .line 87
    invoke-virtual {v3}, Lorg/webrtc/CameraXSession$CameraDeviceId;->getPhysicalId()Ljava/lang/String;

    .line 90
    move-result-object v3

    .line 91
    invoke-static {v2, v3}, Lkotlin/jvm/internal/Intrinsics;->g(Ljava/lang/Object;Ljava/lang/Object;)Z

    .line 94
    move-result v2

    .line 95
    if-eqz v2, :cond_1

    .line 97
    :cond_2
    const-string v0, "first(...)"

    .line 99
    invoke-static {v1, v0}, Lkotlin/jvm/internal/Intrinsics;->o(Ljava/lang/Object;Ljava/lang/String;)V

    .line 102
    check-cast v1, Landroidx/camera/camera2/interop/j;

    .line 104
    sget-object v0, Landroid/hardware/camera2/CameraCharacteristics;->SENSOR_ORIENTATION:Landroid/hardware/camera2/CameraCharacteristics$Key;

    .line 106
    invoke-virtual {v1, v0}, Landroidx/camera/camera2/interop/j;->c(Landroid/hardware/camera2/CameraCharacteristics$Key;)Ljava/lang/Object;

    .line 109
    move-result-object v0

    .line 110
    check-cast v0, Ljava/lang/Integer;

    .line 112
    if-eqz v0, :cond_3

    .line 114
    invoke-virtual {v0}, Ljava/lang/Integer;->intValue()I

    .line 117
    move-result v0

    .line 118
    goto :goto_1

    .line 119
    :cond_3
    const/4 v0, -0x1

    .line 120
    :goto_1
    iput v0, p0, Lorg/webrtc/CameraXSession;->cameraOrientation:I

    .line 122
    sget-object v0, Landroid/hardware/camera2/CameraCharacteristics;->LENS_FACING:Landroid/hardware/camera2/CameraCharacteristics$Key;

    .line 124
    invoke-virtual {v1, v0}, Landroidx/camera/camera2/interop/j;->c(Landroid/hardware/camera2/CameraCharacteristics$Key;)Ljava/lang/Object;

    .line 127
    move-result-object v0

    .line 128
    check-cast v0, Ljava/lang/Integer;

    .line 130
    if-nez v0, :cond_4

    .line 132
    goto :goto_2

    .line 133
    :cond_4
    invoke-virtual {v0}, Ljava/lang/Integer;->intValue()I

    .line 136
    move-result v0

    .line 137
    if-nez v0, :cond_5

    .line 139
    const/4 v0, 0x1

    .line 140
    goto :goto_3

    .line 141
    :cond_5
    :goto_2
    const/4 v0, 0x0

    .line 142
    :goto_3
    iput-boolean v0, p0, Lorg/webrtc/CameraXSession;->isCameraFrontFacing:Z

    .line 144
    invoke-direct {p0, v1}, Lorg/webrtc/CameraXSession;->findCaptureFormat(Landroidx/camera/camera2/interop/j;)V

    .line 147
    invoke-direct {p0, v1}, Lorg/webrtc/CameraXSession;->findStabilizationMode(Landroidx/camera/camera2/interop/j;)V

    .line 150
    return-void

    .line 151
    :cond_6
    new-instance v0, Ljava/util/NoSuchElementException;

    .line 153
    const-string v1, "Collection contains no element matching the predicate."

    .line 155
    invoke-direct {v0, v1}, Ljava/util/NoSuchElementException;-><init>(Ljava/lang/String;)V

    .line 158
    throw v0

    .line 159
    :cond_7
    const-string v0, "cameraProvider"

    .line 161
    invoke-static {v0}, Lkotlin/jvm/internal/Intrinsics;->S(Ljava/lang/String;)V

    .line 164
    const/4 v0, 0x0

    .line 165
    throw v0
.end method

.method private final openCamera()V
    .locals 3

    .prologue
    .line 1
    invoke-direct {p0}, Lorg/webrtc/CameraXSession;->checkIsOnCameraThread()V

    .line 4
    sget-object v0, Lorg/webrtc/CameraXSession;->logger:Lorg/apache/log4j/Logger;

    .line 6
    iget-object v1, p0, Lorg/webrtc/CameraXSession;->cameraId:Ljava/lang/String;

    .line 8
    const-string v2, "Opening camera "

    .line 10
    invoke-static {v2, v1, v0}, Lcom/codebutler/android_websockets/b;->a(Ljava/lang/String;Ljava/lang/String;Lorg/apache/log4j/Logger;)V

    .line 13
    iget-object v0, p0, Lorg/webrtc/CameraXSession;->events:Lorg/webrtc/CameraSession$Events;

    .line 15
    invoke-interface {v0}, Lorg/webrtc/CameraSession$Events;->onCameraOpening()V

    .line 18
    sget-object v0, Landroidx/camera/lifecycle/j;->i:Landroidx/camera/lifecycle/j$a;

    .line 20
    iget-object v1, p0, Lorg/webrtc/CameraXSession;->context:Landroid/content/Context;

    .line 22
    invoke-virtual {v0, v1}, Landroidx/camera/lifecycle/j$a;->c(Landroid/content/Context;)Lcom/google/common/util/concurrent/t1;

    .line 25
    move-result-object v0

    .line 26
    new-instance v1, Lorg/webrtc/s;

    .line 28
    invoke-direct {v1, p0}, Lorg/webrtc/s;-><init>(Lorg/webrtc/CameraXSession;)V

    .line 31
    new-instance v2, Lorg/webrtc/t;

    .line 33
    invoke-direct {v2, p0, v0, v1}, Lorg/webrtc/t;-><init>(Lorg/webrtc/CameraXSession;Lcom/google/common/util/concurrent/t1;Ljava/util/concurrent/Executor;)V

    .line 36
    check-cast v0, Landroidx/camera/core/impl/utils/futures/d;

    .line 38
    invoke-virtual {v0, v2, v1}, Landroidx/camera/core/impl/utils/futures/d;->addListener(Ljava/lang/Runnable;Ljava/util/concurrent/Executor;)V

    .line 41
    return-void
.end method

.method private static final openCamera$lambda$17(Lorg/webrtc/CameraXSession;Lcom/google/common/util/concurrent/t1;Ljava/util/concurrent/Executor;)V
    .locals 2

    .prologue
    .line 1
    invoke-interface {p1}, Ljava/util/concurrent/Future;->get()Ljava/lang/Object;

    .line 4
    move-result-object p1

    .line 5
    check-cast p1, Landroidx/camera/lifecycle/j;

    .line 7
    iput-object p1, p0, Lorg/webrtc/CameraXSession;->cameraProvider:Landroidx/camera/lifecycle/j;

    .line 9
    invoke-direct {p0}, Lorg/webrtc/CameraXSession;->obtainCameraConfiguration()V

    .line 12
    iget-object p1, p0, Lorg/webrtc/CameraXSession;->surfaceTextureHelper:Lorg/webrtc/SurfaceTextureHelper;

    .line 14
    iget-object v0, p0, Lorg/webrtc/CameraXSession;->captureFormat:Lorg/webrtc/CameraEnumerationAndroid$CaptureFormat;

    .line 16
    if-eqz v0, :cond_0

    .line 18
    iget v1, v0, Lorg/webrtc/CameraEnumerationAndroid$CaptureFormat;->width:I

    .line 20
    goto :goto_0

    .line 21
    :cond_0
    iget v1, p0, Lorg/webrtc/CameraXSession;->width:I

    .line 23
    :goto_0
    if-eqz v0, :cond_1

    .line 25
    iget v0, v0, Lorg/webrtc/CameraEnumerationAndroid$CaptureFormat;->height:I

    .line 27
    goto :goto_1

    .line 28
    :cond_1
    iget v0, p0, Lorg/webrtc/CameraXSession;->height:I

    .line 30
    :goto_1
    invoke-virtual {p1, v1, v0}, Lorg/webrtc/SurfaceTextureHelper;->setTextureSize(II)V

    .line 33
    new-instance p1, Landroid/view/Surface;

    .line 35
    iget-object v0, p0, Lorg/webrtc/CameraXSession;->surfaceTextureHelper:Lorg/webrtc/SurfaceTextureHelper;

    .line 37
    invoke-virtual {v0}, Lorg/webrtc/SurfaceTextureHelper;->getSurfaceTexture()Landroid/graphics/SurfaceTexture;

    .line 40
    move-result-object v0

    .line 41
    invoke-direct {p1, v0}, Landroid/view/Surface;-><init>(Landroid/graphics/SurfaceTexture;)V

    .line 44
    iput-object p1, p0, Lorg/webrtc/CameraXSession;->surface:Landroid/view/Surface;

    .line 46
    new-instance p1, Lorg/webrtc/j;

    .line 48
    invoke-direct {p1, p0, p2}, Lorg/webrtc/j;-><init>(Lorg/webrtc/CameraXSession;Ljava/util/concurrent/Executor;)V

    .line 51
    iput-object p1, p0, Lorg/webrtc/CameraXSession;->surfaceProvider:Landroidx/camera/core/b3$c;

    .line 53
    new-instance p1, Landroidx/camera/core/x$a;

    .line 55
    invoke-direct {p1}, Landroidx/camera/core/x$a;-><init>()V

    .line 58
    new-instance p2, Lorg/webrtc/k;

    .line 60
    invoke-direct {p2, p0}, Lorg/webrtc/k;-><init>(Lorg/webrtc/CameraXSession;)V

    .line 63
    invoke-virtual {p1, p2}, Landroidx/camera/core/x$a;->a(Landroidx/camera/core/u;)Landroidx/camera/core/x$a;

    .line 66
    invoke-virtual {p1}, Landroidx/camera/core/x$a;->b()Landroidx/camera/core/x;

    .line 69
    move-result-object p1

    .line 70
    :try_start_0
    iget-object p2, p0, Lorg/webrtc/CameraXSession;->context:Landroid/content/Context;

    .line 72
    invoke-static {p2}, Landroidx/core/content/e;->getMainExecutor(Landroid/content/Context;)Ljava/util/concurrent/Executor;

    .line 75
    move-result-object p2

    .line 76
    new-instance v0, Lorg/webrtc/l;

    .line 78
    invoke-direct {v0, p0, p1}, Lorg/webrtc/l;-><init>(Lorg/webrtc/CameraXSession;Landroidx/camera/core/x;)V

    .line 81
    invoke-interface {p2, v0}, Ljava/util/concurrent/Executor;->execute(Ljava/lang/Runnable;)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    .line 84
    return-void

    .line 85
    :catch_0
    move-exception p1

    .line 86
    new-instance p2, Ljava/lang/StringBuilder;

    .line 88
    const-string v0, "Failed to open camera: "

    .line 90
    invoke-direct {p2, v0}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    .line 93
    invoke-virtual {p2, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    .line 96
    invoke-virtual {p2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    .line 99
    move-result-object p1

    .line 100
    invoke-direct {p0, p1}, Lorg/webrtc/CameraXSession;->reportError(Ljava/lang/String;)V

    .line 103
    return-void
.end method

.method private static final openCamera$lambda$17$lambda$16(Lorg/webrtc/CameraXSession;Landroidx/camera/core/x;)V
    .locals 6

    .prologue
    .line 1
    new-instance v0, Landroidx/camera/core/b3$a;

    .line 3
    invoke-direct {v0}, Landroidx/camera/core/b3$a;-><init>()V

    .line 6
    new-instance v1, Landroidx/camera/core/resolutionselector/c$b;

    .line 8
    invoke-direct {v1}, Landroidx/camera/core/resolutionselector/c$b;-><init>()V

    .line 11
    new-instance v2, Landroidx/camera/core/resolutionselector/d;

    .line 13
    new-instance v3, Landroid/util/Size;

    .line 15
    iget-object v4, p0, Lorg/webrtc/CameraXSession;->captureFormat:Lorg/webrtc/CameraEnumerationAndroid$CaptureFormat;

    .line 17
    if-eqz v4, :cond_0

    .line 19
    iget v5, v4, Lorg/webrtc/CameraEnumerationAndroid$CaptureFormat;->width:I

    .line 21
    goto :goto_0

    .line 22
    :cond_0
    iget v5, p0, Lorg/webrtc/CameraXSession;->width:I

    .line 24
    :goto_0
    if-eqz v4, :cond_1

    .line 26
    iget v4, v4, Lorg/webrtc/CameraEnumerationAndroid$CaptureFormat;->height:I

    .line 28
    goto :goto_1

    .line 29
    :cond_1
    iget v4, p0, Lorg/webrtc/CameraXSession;->height:I

    .line 31
    :goto_1
    invoke-direct {v3, v5, v4}, Landroid/util/Size;-><init>(II)V

    .line 34
    const/4 v4, 0x1

    .line 35
    invoke-direct {v2, v3, v4}, Landroidx/camera/core/resolutionselector/d;-><init>(Landroid/util/Size;I)V

    .line 38
    invoke-virtual {v1, v2}, Landroidx/camera/core/resolutionselector/c$b;->f(Landroidx/camera/core/resolutionselector/d;)Landroidx/camera/core/resolutionselector/c$b;

    .line 41
    invoke-virtual {v1}, Landroidx/camera/core/resolutionselector/c$b;->a()Landroidx/camera/core/resolutionselector/c;

    .line 44
    move-result-object v1

    .line 45
    invoke-virtual {v0, v1}, Landroidx/camera/core/b3$a;->N(Landroidx/camera/core/resolutionselector/c;)Landroidx/camera/core/b3$a;

    .line 48
    invoke-direct {p0, v0}, Lorg/webrtc/CameraXSession;->applyCameraSettings(Landroidx/camera/core/w0;)Landroidx/camera/core/w0;

    .line 51
    move-result-object v0

    .line 52
    invoke-interface {v0}, Landroidx/camera/core/w0;->build()Ljava/lang/Object;

    .line 55
    move-result-object v0

    .line 56
    move-object v1, v0

    .line 57
    check-cast v1, Landroidx/camera/core/b3;

    .line 59
    iget-object v2, p0, Lorg/webrtc/CameraXSession;->surfaceProvider:Landroidx/camera/core/b3$c;

    .line 61
    const/4 v3, 0x0

    .line 62
    if-eqz v2, :cond_7

    .line 64
    invoke-virtual {v1, v2}, Landroidx/camera/core/b3;->y0(Landroidx/camera/core/b3$c;)V

    .line 67
    const-string v1, "also(...)"

    .line 69
    invoke-static {v0, v1}, Lkotlin/jvm/internal/Intrinsics;->o(Ljava/lang/Object;Ljava/lang/String;)V

    .line 72
    check-cast v0, Landroidx/camera/core/b3;

    .line 74
    iget-object v1, p0, Lorg/webrtc/CameraXSession;->cameraProvider:Landroidx/camera/lifecycle/j;

    .line 76
    const-string v2, "cameraProvider"

    .line 78
    if-eqz v1, :cond_6

    .line 80
    invoke-virtual {v1}, Landroidx/camera/lifecycle/j;->c()V

    .line 83
    iget-object v1, p0, Lorg/webrtc/CameraXSession;->cameraProvider:Landroidx/camera/lifecycle/j;

    .line 85
    if-eqz v1, :cond_5

    .line 87
    iget-object v2, p0, Lorg/webrtc/CameraXSession;->lifecycleOwner:Landroidx/lifecycle/o0;

    .line 89
    new-instance v4, Lkotlin/jvm/internal/SpreadBuilder;

    .line 91
    const/4 v5, 0x2

    .line 92
    invoke-direct {v4, v5}, Lkotlin/jvm/internal/SpreadBuilder;-><init>(I)V

    .line 95
    invoke-virtual {v4, v0}, Lkotlin/jvm/internal/SpreadBuilder;->a(Ljava/lang/Object;)V

    .line 98
    iget-object v0, p0, Lorg/webrtc/CameraXSession;->useCases:[Landroidx/camera/core/a4;

    .line 100
    invoke-virtual {v4, v0}, Lkotlin/jvm/internal/SpreadBuilder;->b(Ljava/lang/Object;)V

    .line 103
    invoke-virtual {v4}, Lkotlin/jvm/internal/SpreadBuilder;->c()I

    .line 106
    move-result v0

    .line 107
    new-array v0, v0, [Landroidx/camera/core/a4;

    .line 109
    invoke-virtual {v4, v0}, Lkotlin/jvm/internal/SpreadBuilder;->d([Ljava/lang/Object;)[Ljava/lang/Object;

    .line 112
    move-result-object v0

    .line 113
    check-cast v0, [Landroidx/camera/core/a4;

    .line 115
    invoke-virtual {v1, v2, p1, v0}, Landroidx/camera/lifecycle/j;->D(Landroidx/lifecycle/o0;Landroidx/camera/core/x;[Landroidx/camera/core/a4;)Landroidx/camera/core/p;

    .line 118
    move-result-object p1

    .line 119
    iput-object p1, p0, Lorg/webrtc/CameraXSession;->camera:Landroidx/camera/core/p;

    .line 121
    check-cast p1, Landroidx/camera/lifecycle/c;

    .line 123
    invoke-virtual {p1}, Landroidx/camera/lifecycle/c;->b()Landroidx/camera/core/v;

    .line 126
    move-result-object p1

    .line 127
    if-eqz p1, :cond_2

    .line 129
    invoke-interface {p1}, Landroidx/camera/core/v;->s()Landroidx/lifecycle/v0;

    .line 132
    move-result-object v0

    .line 133
    if-eqz v0, :cond_2

    .line 135
    invoke-virtual {v0}, Landroidx/lifecycle/v0;->f()Ljava/lang/Object;

    .line 138
    move-result-object v0

    .line 139
    check-cast v0, Landroidx/camera/core/d4;

    .line 141
    goto :goto_2

    .line 142
    :cond_2
    move-object v0, v3

    .line 143
    :goto_2
    if-eqz p1, :cond_3

    .line 145
    invoke-interface {p1}, Landroidx/camera/core/v;->v()Z

    .line 148
    move-result p1

    .line 149
    invoke-static {p1}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;

    .line 152
    move-result-object v3

    .line 153
    :cond_3
    sget-object p1, Lorg/webrtc/CameraXSession;->logger:Lorg/apache/log4j/Logger;

    .line 155
    new-instance v1, Ljava/lang/StringBuilder;

    .line 157
    const-string v2, "Camera info: zoomState: "

    .line 159
    invoke-direct {v1, v2}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    .line 162
    invoke-virtual {v1, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    .line 165
    const-string v0, ", isLogicalMultiCameraSupported: "

    .line 167
    invoke-virtual {v1, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 170
    invoke-virtual {v1, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    .line 173
    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    .line 176
    move-result-object v0

    .line 177
    invoke-virtual {p1, v0}, Lorg/apache/log4j/Category;->debug(Ljava/lang/Object;)V

    .line 180
    iget-object p1, p0, Lorg/webrtc/CameraXSession;->cameraThreadHandler:Landroid/os/Handler;

    .line 182
    new-instance v0, Lorg/webrtc/u;

    .line 184
    invoke-direct {v0, p0}, Lorg/webrtc/u;-><init>(Lorg/webrtc/CameraXSession;)V

    .line 187
    invoke-virtual {p1, v0}, Landroid/os/Handler;->post(Ljava/lang/Runnable;)Z

    .line 190
    iget-object p1, p0, Lorg/webrtc/CameraXSession;->camera:Landroidx/camera/core/p;

    .line 192
    if-eqz p1, :cond_4

    .line 194
    invoke-interface {p1}, Landroidx/camera/core/p;->b()Landroidx/camera/core/v;

    .line 197
    move-result-object p1

    .line 198
    if-eqz p1, :cond_4

    .line 200
    invoke-interface {p1}, Landroidx/camera/core/v;->c()Landroidx/lifecycle/v0;

    .line 203
    move-result-object p1

    .line 204
    if-eqz p1, :cond_4

    .line 206
    iget-object v0, p0, Lorg/webrtc/CameraXSession;->lifecycleOwner:Landroidx/lifecycle/o0;

    .line 208
    new-instance v1, Lorg/webrtc/v;

    .line 210
    invoke-direct {v1, p0}, Lorg/webrtc/v;-><init>(Lorg/webrtc/CameraXSession;)V

    .line 213
    new-instance p0, Lorg/webrtc/i;

    .line 215
    invoke-direct {p0, v1}, Lorg/webrtc/i;-><init>(Lkotlin/jvm/functions/Function1;)V

    .line 218
    invoke-virtual {p1, v0, p0}, Landroidx/lifecycle/v0;->k(Landroidx/lifecycle/o0;Landroidx/lifecycle/c1;)V

    .line 221
    :cond_4
    return-void

    .line 222
    :cond_5
    invoke-static {v2}, Lkotlin/jvm/internal/Intrinsics;->S(Ljava/lang/String;)V

    .line 225
    throw v3

    .line 226
    :cond_6
    invoke-static {v2}, Lkotlin/jvm/internal/Intrinsics;->S(Ljava/lang/String;)V

    .line 229
    throw v3

    .line 230
    :cond_7
    const-string p0, "surfaceProvider"

    .line 232
    invoke-static {p0}, Lkotlin/jvm/internal/Intrinsics;->S(Ljava/lang/String;)V

    .line 235
    throw v3
.end method

.method private static final openCamera$lambda$17$lambda$16$lambda$11(Lorg/webrtc/CameraXSession;)V
    .locals 1

    .prologue
    .line 1
    iget-object v0, p0, Lorg/webrtc/CameraXSession;->sessionCallback:Lorg/webrtc/CameraSession$CreateSessionCallback;

    .line 3
    invoke-interface {v0, p0}, Lorg/webrtc/CameraSession$CreateSessionCallback;->onDone(Lorg/webrtc/CameraSession;)V

    .line 6
    return-void
.end method

.method private static final openCamera$lambda$17$lambda$16$lambda$14(Lorg/webrtc/CameraXSession;Landroidx/camera/core/y;)Lkotlin/Unit;
    .locals 2

    .prologue
    .line 1
    iget-object v0, p0, Lorg/webrtc/CameraXSession;->cameraThreadHandler:Landroid/os/Handler;

    .line 3
    new-instance v1, Lorg/webrtc/m;

    .line 5
    invoke-direct {v1, p0, p1}, Lorg/webrtc/m;-><init>(Lorg/webrtc/CameraXSession;Landroidx/camera/core/y;)V

    .line 8
    invoke-virtual {v0, v1}, Landroid/os/Handler;->postAtFrontOfQueue(Ljava/lang/Runnable;)Z

    .line 11
    sget-object p0, Lkotlin/Unit;->a:Lkotlin/Unit;

    .line 13
    return-object p0
.end method

.method private static final openCamera$lambda$17$lambda$16$lambda$14$lambda$13(Lorg/webrtc/CameraXSession;Landroidx/camera/core/y;)V
    .locals 6

    .prologue
    .line 1
    sget-object v0, Lorg/webrtc/CameraXSession;->logger:Lorg/apache/log4j/Logger;

    .line 3
    iget-object v1, p0, Lorg/webrtc/CameraXSession;->cameraId:Ljava/lang/String;

    .line 5
    invoke-virtual {p1}, Landroidx/camera/core/y;->d()Landroidx/camera/core/y$c;

    .line 8
    move-result-object v2

    .line 9
    iget-object v3, p0, Lorg/webrtc/CameraXSession;->sessionState:Lorg/webrtc/CameraXSession$SessionState;

    .line 11
    new-instance v4, Ljava/lang/StringBuilder;

    .line 13
    const-string v5, "Camera "

    .line 15
    invoke-direct {v4, v5}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    .line 18
    invoke-virtual {v4, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 21
    const-string v1, " state changed to "

    .line 23
    invoke-virtual {v4, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 26
    invoke-virtual {v4, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    .line 29
    const-string v1, ", session state: "

    .line 31
    invoke-virtual {v4, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 34
    invoke-virtual {v4, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    .line 37
    invoke-virtual {v4}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    .line 40
    move-result-object v1

    .line 41
    invoke-virtual {v0, v1}, Lorg/apache/log4j/Category;->debug(Ljava/lang/Object;)V

    .line 44
    invoke-virtual {p1}, Landroidx/camera/core/y;->d()Landroidx/camera/core/y$c;

    .line 47
    move-result-object v0

    .line 48
    sget-object v1, Landroidx/camera/core/y$c;->CLOSED:Landroidx/camera/core/y$c;

    .line 50
    if-ne v0, v1, :cond_0

    .line 52
    iget-object v0, p0, Lorg/webrtc/CameraXSession;->sessionState:Lorg/webrtc/CameraXSession$SessionState;

    .line 54
    sget-object v1, Lorg/webrtc/CameraXSession$SessionState;->STOPPED:Lorg/webrtc/CameraXSession$SessionState;

    .line 56
    if-ne v0, v1, :cond_0

    .line 58
    iget-object v0, p0, Lorg/webrtc/CameraXSession;->events:Lorg/webrtc/CameraSession$Events;

    .line 60
    invoke-interface {v0, p0}, Lorg/webrtc/CameraSession$Events;->onCameraClosed(Lorg/webrtc/CameraSession;)V

    .line 63
    :cond_0
    invoke-virtual {p1}, Landroidx/camera/core/y;->c()Landroidx/camera/core/y$b;

    .line 66
    move-result-object p1

    .line 67
    if-eqz p1, :cond_2

    .line 69
    invoke-virtual {p1}, Landroidx/camera/core/y$b;->d()I

    .line 72
    move-result v0

    .line 73
    const/4 v1, 0x4

    .line 74
    if-ne v0, v1, :cond_1

    .line 76
    new-instance v0, Ljava/lang/StringBuilder;

    .line 78
    const-string v1, "Stream configuration error: "

    .line 80
    invoke-direct {v0, v1}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    .line 83
    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    .line 86
    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    .line 89
    move-result-object p1

    .line 90
    invoke-direct {p0, p1}, Lorg/webrtc/CameraXSession;->reportError(Ljava/lang/String;)V

    .line 93
    return-void

    .line 94
    :cond_1
    new-instance v0, Ljava/lang/StringBuilder;

    .line 96
    const-string v1, "Camera other error: "

    .line 98
    invoke-direct {v0, v1}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    .line 101
    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    .line 104
    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    .line 107
    move-result-object p1

    .line 108
    invoke-direct {p0, p1}, Lorg/webrtc/CameraXSession;->reportError(Ljava/lang/String;)V

    .line 111
    :cond_2
    return-void
.end method

.method private static final openCamera$lambda$17$lambda$16$lambda$15(Lkotlin/jvm/functions/Function1;Ljava/lang/Object;)V
    .locals 0

    .prologue
    .line 1
    invoke-interface {p0, p1}, Lkotlin/jvm/functions/Function1;->invoke(Ljava/lang/Object;)Ljava/lang/Object;

    .line 4
    return-void
.end method

.method private static final openCamera$lambda$17$lambda$7(Lorg/webrtc/CameraXSession;Ljava/util/concurrent/Executor;Landroidx/camera/core/y3;)V
    .locals 1

    .prologue
    .line 1
    const-string v0, "request"

    .line 3
    invoke-static {p2, v0}, Lkotlin/jvm/internal/Intrinsics;->p(Ljava/lang/Object;Ljava/lang/String;)V

    .line 6
    iget-object p0, p0, Lorg/webrtc/CameraXSession;->surface:Landroid/view/Surface;

    .line 8
    if-eqz p0, :cond_0

    .line 10
    new-instance v0, Lorg/webrtc/o;

    .line 12
    invoke-direct {v0}, Ljava/lang/Object;-><init>()V

    .line 15
    invoke-virtual {p2, p0, p1, v0}, Landroidx/camera/core/y3;->u(Landroid/view/Surface;Ljava/util/concurrent/Executor;Landroidx/core/util/e;)V

    .line 18
    return-void

    .line 19
    :cond_0
    invoke-virtual {p2}, Landroidx/camera/core/y3;->x()Z

    .line 22
    return-void
.end method

.method private static final openCamera$lambda$17$lambda$7$lambda$6$lambda$5(Landroidx/camera/core/y3$g;)V
    .locals 0

    .prologue
    .line 1
    return-void
.end method

.method private static final openCamera$lambda$17$lambda$9(Lorg/webrtc/CameraXSession;Ljava/util/List;)Ljava/util/List;
    .locals 4

    .prologue
    .line 1
    const-string v0, "cameraInfo"

    .line 3
    invoke-static {p1, v0}, Lkotlin/jvm/internal/Intrinsics;->p(Ljava/lang/Object;Ljava/lang/String;)V

    .line 6
    check-cast p1, Ljava/lang/Iterable;

    .line 8
    new-instance v0, Ljava/util/ArrayList;

    .line 10
    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    .line 13
    invoke-interface {p1}, Ljava/lang/Iterable;->iterator()Ljava/util/Iterator;

    .line 16
    move-result-object p1

    .line 17
    :cond_0
    :goto_0
    invoke-interface {p1}, Ljava/util/Iterator;->hasNext()Z

    .line 20
    move-result v1

    .line 21
    if-eqz v1, :cond_2

    .line 23
    invoke-interface {p1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    .line 26
    move-result-object v1

    .line 27
    move-object v2, v1

    .line 28
    check-cast v2, Landroidx/camera/core/v;

    .line 30
    invoke-static {v2}, Landroidx/camera/camera2/interop/j;->b(Landroidx/camera/core/v;)Landroidx/camera/camera2/interop/j;

    .line 33
    move-result-object v2

    .line 34
    invoke-virtual {v2}, Landroidx/camera/camera2/interop/j;->e()Ljava/lang/String;

    .line 37
    move-result-object v2

    .line 38
    const-string v3, "getCameraId(...)"

    .line 40
    invoke-static {v2, v3}, Lkotlin/jvm/internal/Intrinsics;->o(Ljava/lang/Object;Ljava/lang/String;)V

    .line 43
    invoke-direct {p0}, Lorg/webrtc/CameraXSession;->getCameraDevice()Lorg/webrtc/CameraXSession$CameraDeviceId;

    .line 46
    move-result-object v3

    .line 47
    invoke-virtual {v3}, Lorg/webrtc/CameraXSession$CameraDeviceId;->getDeviceId()Ljava/lang/String;

    .line 50
    move-result-object v3

    .line 51
    invoke-virtual {v2, v3}, Ljava/lang/Object;->equals(Ljava/lang/Object;)Z

    .line 54
    move-result v3

    .line 55
    if-nez v3, :cond_1

    .line 57
    invoke-direct {p0}, Lorg/webrtc/CameraXSession;->getCameraDevice()Lorg/webrtc/CameraXSession$CameraDeviceId;

    .line 60
    move-result-object v3

    .line 61
    invoke-virtual {v3}, Lorg/webrtc/CameraXSession$CameraDeviceId;->getPhysicalId()Ljava/lang/String;

    .line 64
    move-result-object v3

    .line 65
    invoke-virtual {v2, v3}, Ljava/lang/Object;->equals(Ljava/lang/Object;)Z

    .line 68
    move-result v2

    .line 69
    if-eqz v2, :cond_0

    .line 71
    :cond_1
    invoke-interface {v0, v1}, Ljava/util/Collection;->add(Ljava/lang/Object;)Z

    .line 74
    goto :goto_0

    .line 75
    :cond_2
    return-object v0
.end method

.method private static final openCamera$lambda$4(Lorg/webrtc/CameraXSession;Ljava/lang/Runnable;)V
    .locals 1

    .prologue
    .line 1
    iget-object p0, p0, Lorg/webrtc/CameraXSession;->surfaceTextureHelper:Lorg/webrtc/SurfaceTextureHelper;

    .line 3
    invoke-virtual {p0}, Lorg/webrtc/SurfaceTextureHelper;->getHandler()Landroid/os/Handler;

    .line 6
    move-result-object p0

    .line 7
    invoke-virtual {p0}, Landroid/os/Handler;->getLooper()Landroid/os/Looper;

    .line 10
    move-result-object v0

    .line 11
    invoke-virtual {v0}, Landroid/os/Looper;->getThread()Ljava/lang/Thread;

    .line 14
    move-result-object v0

    .line 15
    invoke-virtual {v0}, Ljava/lang/Thread;->isAlive()Z

    .line 18
    move-result v0

    .line 19
    if-eqz v0, :cond_0

    .line 21
    invoke-virtual {p0, p1}, Landroid/os/Handler;->post(Ljava/lang/Runnable;)Z

    .line 24
    :cond_0
    return-void
.end method

.method private final reportError(Ljava/lang/String;)V
    .locals 2

    .prologue
    .line 1
    invoke-direct {p0}, Lorg/webrtc/CameraXSession;->checkIsOnCameraThread()V

    .line 4
    sget-object v0, Lorg/webrtc/CameraXSession;->logger:Lorg/apache/log4j/Logger;

    .line 6
    const-string v1, "Error: "

    .line 8
    invoke-static {v1, p1, v0}, Lcom/sand/airdroid/d;->a(Ljava/lang/String;Ljava/lang/String;Lorg/apache/log4j/Logger;)V

    .line 11
    iget-object v0, p0, Lorg/webrtc/CameraXSession;->camera:Landroidx/camera/core/p;

    .line 13
    if-nez v0, :cond_0

    .line 15
    iget-object v0, p0, Lorg/webrtc/CameraXSession;->sessionState:Lorg/webrtc/CameraXSession$SessionState;

    .line 17
    sget-object v1, Lorg/webrtc/CameraXSession$SessionState;->STOPPED:Lorg/webrtc/CameraXSession$SessionState;

    .line 19
    if-eq v0, v1, :cond_0

    .line 21
    const/4 v0, 0x1

    .line 22
    goto :goto_0

    .line 23
    :cond_0
    const/4 v0, 0x0

    .line 24
    :goto_0
    sget-object v1, Lorg/webrtc/CameraXSession$SessionState;->STOPPED:Lorg/webrtc/CameraXSession$SessionState;

    .line 26
    iput-object v1, p0, Lorg/webrtc/CameraXSession;->sessionState:Lorg/webrtc/CameraXSession$SessionState;

    .line 28
    invoke-direct {p0}, Lorg/webrtc/CameraXSession;->stopInternal()V

    .line 31
    if-eqz v0, :cond_1

    .line 33
    iget-object v0, p0, Lorg/webrtc/CameraXSession;->sessionCallback:Lorg/webrtc/CameraSession$CreateSessionCallback;

    .line 35
    sget-object v1, Lorg/webrtc/CameraSession$FailureType;->ERROR:Lorg/webrtc/CameraSession$FailureType;

    .line 37
    invoke-interface {v0, v1, p1}, Lorg/webrtc/CameraSession$CreateSessionCallback;->onFailure(Lorg/webrtc/CameraSession$FailureType;Ljava/lang/String;)V

    .line 40
    return-void

    .line 41
    :cond_1
    iget-object v0, p0, Lorg/webrtc/CameraXSession;->events:Lorg/webrtc/CameraSession$Events;

    .line 43
    invoke-interface {v0, p0, p1}, Lorg/webrtc/CameraSession$Events;->onCameraError(Lorg/webrtc/CameraSession;Ljava/lang/String;)V

    .line 46
    return-void
.end method

.method private final start()V
    .locals 3

    .prologue
    .line 1
    invoke-direct {p0}, Lorg/webrtc/CameraXSession;->checkIsOnCameraThread()V

    .line 4
    sget-object v0, Lorg/webrtc/CameraXSession;->logger:Lorg/apache/log4j/Logger;

    .line 6
    const-string v1, "start"

    .line 8
    invoke-virtual {v0, v1}, Lorg/apache/log4j/Category;->debug(Ljava/lang/Object;)V

    .line 11
    iget-object v0, p0, Lorg/webrtc/CameraXSession;->surfaceTextureHelper:Lorg/webrtc/SurfaceTextureHelper;

    .line 13
    iget-object v1, p0, Lorg/webrtc/CameraXSession;->surfaceTextureListener:Lkotlin/jvm/functions/Function1;

    .line 15
    new-instance v2, Lorg/webrtc/h;

    .line 17
    invoke-direct {v2, v1}, Lorg/webrtc/h;-><init>(Lkotlin/jvm/functions/Function1;)V

    .line 20
    invoke-virtual {v0, v2}, Lorg/webrtc/SurfaceTextureHelper;->startListening(Lorg/webrtc/VideoSink;)V

    .line 23
    invoke-direct {p0}, Lorg/webrtc/CameraXSession;->openCamera()V

    .line 26
    return-void
.end method

.method private static final start$lambda$2(Lkotlin/jvm/functions/Function1;Lorg/webrtc/VideoFrame;)V
    .locals 0

    .prologue
    .line 1
    invoke-interface {p0, p1}, Lkotlin/jvm/functions/Function1;->invoke(Ljava/lang/Object;)Ljava/lang/Object;

    .line 4
    return-void
.end method

.method private final stopInternal()V
    .locals 2

    .prologue
    .line 1
    sget-object v0, Lorg/webrtc/CameraXSession;->logger:Lorg/apache/log4j/Logger;

    .line 3
    const-string v1, "Stop internal"

    .line 5
    invoke-virtual {v0, v1}, Lorg/apache/log4j/Category;->debug(Ljava/lang/Object;)V

    .line 8
    invoke-direct {p0}, Lorg/webrtc/CameraXSession;->checkIsOnCameraThread()V

    .line 11
    iget-object v0, p0, Lorg/webrtc/CameraXSession;->surfaceTextureHelper:Lorg/webrtc/SurfaceTextureHelper;

    .line 13
    invoke-virtual {v0}, Lorg/webrtc/SurfaceTextureHelper;->stopListening()V

    .line 16
    iget-object v0, p0, Lorg/webrtc/CameraXSession;->surface:Landroid/view/Surface;

    .line 18
    if-eqz v0, :cond_0

    .line 20
    invoke-static {v0}, Lkotlin/jvm/internal/Intrinsics;->m(Ljava/lang/Object;)V

    .line 23
    invoke-virtual {v0}, Landroid/view/Surface;->release()V

    .line 26
    const/4 v0, 0x0

    .line 27
    iput-object v0, p0, Lorg/webrtc/CameraXSession;->surface:Landroid/view/Surface;

    .line 29
    :cond_0
    iget-object v0, p0, Lorg/webrtc/CameraXSession;->context:Landroid/content/Context;

    .line 31
    invoke-static {v0}, Landroidx/core/content/e;->getMainExecutor(Landroid/content/Context;)Ljava/util/concurrent/Executor;

    .line 34
    move-result-object v0

    .line 35
    new-instance v1, Lorg/webrtc/n;

    .line 37
    invoke-direct {v1, p0}, Lorg/webrtc/n;-><init>(Lorg/webrtc/CameraXSession;)V

    .line 40
    invoke-interface {v0, v1}, Ljava/util/concurrent/Executor;->execute(Ljava/lang/Runnable;)V

    .line 43
    return-void
.end method

.method private static final stopInternal$lambda$19(Lorg/webrtc/CameraXSession;)V
    .locals 0

    .prologue
    .line 1
    iget-object p0, p0, Lorg/webrtc/CameraXSession;->cameraProvider:Landroidx/camera/lifecycle/j;

    .line 3
    if-eqz p0, :cond_0

    .line 5
    invoke-virtual {p0}, Landroidx/camera/lifecycle/j;->c()V

    .line 8
    return-void

    .line 9
    :cond_0
    const-string p0, "cameraProvider"

    .line 11
    invoke-static {p0}, Lkotlin/jvm/internal/Intrinsics;->S(Ljava/lang/String;)V

    .line 14
    const/4 p0, 0x0

    .line 15
    throw p0
.end method

.method private static final surfaceTextureListener$lambda$0(Lorg/webrtc/CameraXSession;Lorg/webrtc/VideoFrame;)Lkotlin/Unit;
    .locals 5

    .prologue
    .line 1
    const-string v0, "frame"

    .line 3
    invoke-static {p1, v0}, Lkotlin/jvm/internal/Intrinsics;->p(Ljava/lang/Object;Ljava/lang/String;)V

    .line 6
    invoke-direct {p0}, Lorg/webrtc/CameraXSession;->checkIsOnCameraThread()V

    .line 9
    iget-object v0, p0, Lorg/webrtc/CameraXSession;->sessionState:Lorg/webrtc/CameraXSession$SessionState;

    .line 11
    sget-object v1, Lorg/webrtc/CameraXSession$SessionState;->RUNNING:Lorg/webrtc/CameraXSession$SessionState;

    .line 13
    if-eq v0, v1, :cond_0

    .line 15
    sget-object p0, Lorg/webrtc/CameraXSession;->logger:Lorg/apache/log4j/Logger;

    .line 17
    const-string p1, "Texture frame captured but camera is no longer running."

    .line 19
    invoke-virtual {p0, p1}, Lorg/apache/log4j/Category;->debug(Ljava/lang/Object;)V

    .line 22
    goto :goto_0

    .line 23
    :cond_0
    iget-boolean v0, p0, Lorg/webrtc/CameraXSession;->firstFrameReported:Z

    .line 25
    if-nez v0, :cond_1

    .line 27
    const/4 v0, 0x1

    .line 28
    iput-boolean v0, p0, Lorg/webrtc/CameraXSession;->firstFrameReported:Z

    .line 30
    sget-object v0, Ljava/util/concurrent/TimeUnit;->NANOSECONDS:Ljava/util/concurrent/TimeUnit;

    .line 32
    invoke-static {}, Ljava/lang/System;->nanoTime()J

    .line 35
    move-result-wide v1

    .line 36
    iget-wide v3, p0, Lorg/webrtc/CameraXSession;->constructionTimeNs:J

    .line 38
    sub-long/2addr v1, v3

    .line 39
    invoke-virtual {v0, v1, v2}, Ljava/util/concurrent/TimeUnit;->toMillis(J)J

    .line 42
    move-result-wide v0

    .line 43
    long-to-int v0, v0

    .line 44
    sget-object v1, Lorg/webrtc/CameraXSession;->cameraXStartTimeMsHistogram:Lorg/webrtc/Histogram;

    .line 46
    invoke-virtual {v1, v0}, Lorg/webrtc/Histogram;->addSample(I)V

    .line 49
    :cond_1
    new-instance v0, Lorg/webrtc/VideoFrame;

    .line 51
    invoke-virtual {p1}, Lorg/webrtc/VideoFrame;->getBuffer()Lorg/webrtc/VideoFrame$Buffer;

    .line 54
    move-result-object v1

    .line 55
    const-string v2, "null cannot be cast to non-null type org.webrtc.TextureBufferImpl"

    .line 57
    invoke-static {v1, v2}, Lkotlin/jvm/internal/Intrinsics;->n(Ljava/lang/Object;Ljava/lang/String;)V

    .line 60
    check-cast v1, Lorg/webrtc/TextureBufferImpl;

    .line 62
    iget-boolean v2, p0, Lorg/webrtc/CameraXSession;->isCameraFrontFacing:Z

    .line 64
    iget v3, p0, Lorg/webrtc/CameraXSession;->cameraOrientation:I

    .line 66
    neg-int v3, v3

    .line 67
    invoke-static {v1, v2, v3}, Lorg/webrtc/CameraSession;->createTextureBufferWithModifiedTransformMatrix(Lorg/webrtc/TextureBufferImpl;ZI)Lorg/webrtc/VideoFrame$TextureBuffer;

    .line 70
    move-result-object v1

    .line 71
    invoke-virtual {p0}, Lorg/webrtc/CameraXSession;->getFrameOrientation()I

    .line 74
    move-result v2

    .line 75
    invoke-virtual {p1}, Lorg/webrtc/VideoFrame;->getTimestampNs()J

    .line 78
    move-result-wide v3

    .line 79
    invoke-direct {v0, v1, v2, v3, v4}, Lorg/webrtc/VideoFrame;-><init>(Lorg/webrtc/VideoFrame$Buffer;IJ)V

    .line 82
    iget-object p1, p0, Lorg/webrtc/CameraXSession;->events:Lorg/webrtc/CameraSession$Events;

    .line 84
    invoke-interface {p1, p0, v0}, Lorg/webrtc/CameraSession$Events;->onFrameCaptured(Lorg/webrtc/CameraSession;Lorg/webrtc/VideoFrame;)V

    .line 87
    invoke-virtual {v0}, Lorg/webrtc/VideoFrame;->release()V

    .line 90
    :goto_0
    sget-object p0, Lkotlin/Unit;->a:Lkotlin/Unit;

    .line 92
    return-object p0
.end method


# virtual methods
.method public final getCamera()Landroidx/camera/core/p;
    .locals 1
    .annotation build Lod/m;
    .end annotation

    .prologue
    .line 1
    iget-object v0, p0, Lorg/webrtc/CameraXSession;->camera:Landroidx/camera/core/p;

    .line 3
    return-object v0
.end method

.method public final getFrameOrientation()I
    .locals 2

    .prologue
    .line 1
    iget-object v0, p0, Lorg/webrtc/CameraXSession;->context:Landroid/content/Context;

    .line 3
    invoke-static {v0}, Lorg/webrtc/CameraSession;->getDeviceOrientation(Landroid/content/Context;)I

    .line 6
    move-result v0

    .line 7
    iget-boolean v1, p0, Lorg/webrtc/CameraXSession;->isCameraFrontFacing:Z

    .line 9
    if-nez v1, :cond_0

    .line 11
    rsub-int v0, v0, 0x168

    .line 13
    :cond_0
    iget v1, p0, Lorg/webrtc/CameraXSession;->cameraOrientation:I

    .line 15
    add-int/2addr v1, v0

    .line 16
    rem-int/lit16 v1, v1, 0x168

    .line 18
    return v1
.end method

.method public final isCameraFrontFacing()Z
    .locals 1

    .prologue
    .line 1
    iget-boolean v0, p0, Lorg/webrtc/CameraXSession;->isCameraFrontFacing:Z

    .line 3
    return v0
.end method

.method public stop()V
    .locals 6

    .prologue
    .line 1
    sget-object v0, Lorg/webrtc/CameraXSession;->logger:Lorg/apache/log4j/Logger;

    .line 3
    iget-object v1, p0, Lorg/webrtc/CameraXSession;->cameraId:Ljava/lang/String;

    .line 5
    const-string v2, "Stop cameraX session on camera "

    .line 7
    invoke-static {v2, v1, v0}, Lcom/codebutler/android_websockets/b;->a(Ljava/lang/String;Ljava/lang/String;Lorg/apache/log4j/Logger;)V

    .line 10
    invoke-direct {p0}, Lorg/webrtc/CameraXSession;->checkIsOnCameraThread()V

    .line 13
    iget-object v0, p0, Lorg/webrtc/CameraXSession;->sessionState:Lorg/webrtc/CameraXSession$SessionState;

    .line 15
    sget-object v1, Lorg/webrtc/CameraXSession$SessionState;->STOPPED:Lorg/webrtc/CameraXSession$SessionState;

    .line 17
    if-eq v0, v1, :cond_0

    .line 19
    invoke-static {}, Ljava/lang/System;->nanoTime()J

    .line 22
    move-result-wide v2

    .line 23
    iput-object v1, p0, Lorg/webrtc/CameraXSession;->sessionState:Lorg/webrtc/CameraXSession$SessionState;

    .line 25
    invoke-direct {p0}, Lorg/webrtc/CameraXSession;->stopInternal()V

    .line 28
    sget-object v0, Ljava/util/concurrent/TimeUnit;->NANOSECONDS:Ljava/util/concurrent/TimeUnit;

    .line 30
    invoke-static {}, Ljava/lang/System;->nanoTime()J

    .line 33
    move-result-wide v4

    .line 34
    sub-long/2addr v4, v2

    .line 35
    invoke-virtual {v0, v4, v5}, Ljava/util/concurrent/TimeUnit;->toMillis(J)J

    .line 38
    move-result-wide v0

    .line 39
    long-to-int v0, v0

    .line 40
    sget-object v1, Lorg/webrtc/CameraXSession;->cameraXStopTimeMsHistogram:Lorg/webrtc/Histogram;

    .line 42
    invoke-virtual {v1, v0}, Lorg/webrtc/Histogram;->addSample(I)V

    .line 45
    :cond_0
    return-void
.end method
