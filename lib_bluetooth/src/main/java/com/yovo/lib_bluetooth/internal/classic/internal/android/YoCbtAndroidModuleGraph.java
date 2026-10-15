package com.yovo.lib_bluetooth.internal.classic.internal.android;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.bluetooth.YoBluetoothAdapterProvider;
import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermissionChecker;
import com.yovo.lib_bluetooth.internal.classic.api.CbtInternalClientConfig;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentityResolver;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshotStore;
import com.yovo.lib_bluetooth.internal.classic.discovery.YoCbtDiscoveryController;
import com.yovo.lib_bluetooth.internal.classic.discovery.YoCbtDiscoveryPlatform;
import com.yovo.lib_bluetooth.internal.classic.discovery.YoCbtDiscoveryResultMerger;
import com.yovo.lib_bluetooth.internal.classic.internal.android.discovery.YoCbtAndroidDiscoveryPlatform;
import com.yovo.lib_bluetooth.internal.classic.internal.android.pairing.YoCbtAndroidBondOperationPlatform;
import com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.YoCbtAndroidProfileBroadcastHandler;
import com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.YoCbtAndroidConnectedDeviceQuery;
import com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.YoCbtAndroidProfileStateQuerier;
import com.yovo.lib_bluetooth.internal.classic.internal.android.receiver.YoCbtAclReceiverAdapter;
import com.yovo.lib_bluetooth.internal.classic.internal.android.receiver.YoCbtBondStateReceiverAdapter;
import com.yovo.lib_bluetooth.internal.classic.internal.android.receiver.YoCbtDiscoveryReceiverAdapter;
import com.yovo.lib_bluetooth.internal.classic.internal.android.receiver.YoCbtProfileReceiverAdapter;
import com.yovo.lib_bluetooth.internal.classic.internal.android.receiver.YoCbtSdpReceiverAdapter;
import com.yovo.lib_bluetooth.internal.classic.internal.android.spp.YoCbtSppRemoteDeviceGateway;
import com.yovo.lib_bluetooth.internal.classic.link.YoCbtAclLinkRepository;
import com.yovo.lib_bluetooth.internal.classic.link.YoCbtAclLinkTracker;
import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondOperationController;
import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondedDeviceCoordinator;
import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondedDeviceLoader;
import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondedDeviceProvider;
import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondedDeviceRepository;
import com.yovo.lib_bluetooth.internal.classic.profiles.a2dp.YoCbtA2dpProfileController;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileRegistry;
import com.yovo.lib_bluetooth.internal.classic.profiles.extensions.YoCbtProfileExtensionRegistry;
import com.yovo.lib_bluetooth.internal.classic.profiles.headset.YoCbtHeadsetProfileController;
import com.yovo.lib_bluetooth.internal.classic.profiles.headset.YoCbtScoAudioStateTracker;
import com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.a2dp.YoCbtAndroidA2dpProfilePort;
import com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.extensions.YoCbtAndroidProfileExtensionBootstrap;
import com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.headset.YoCbtAndroidHeadsetProfilePort;
import com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.proxy.YoCbtAndroidProfileProxyManager;
import com.yovo.lib_bluetooth.internal.classic.internal.android.pairing.YoCbtAndroidBondedDeviceLoader;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileBroadcastApplier;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileStateRepository;
import com.yovo.lib_bluetooth.internal.classic.protocol.command.YoCbtCommandSessionRegistry;
import com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtReleaseCoordinator;
import com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtRuntime;
import com.yovo.lib_bluetooth.internal.classic.service.cache.YoCbtServiceCache;
import com.yovo.lib_bluetooth.internal.classic.service.channel.YoCbtServiceChannelRegistry;
import com.yovo.lib_bluetooth.internal.classic.service.discovery.YoCbtServiceDiscoveryClient;
import com.yovo.lib_bluetooth.internal.classic.service.resolve.YoCbtProfileUuidMapper;
import com.yovo.lib_bluetooth.internal.classic.service.resolve.YoCbtSdpResolver;
import com.yovo.lib_bluetooth.internal.classic.service.resolve.YoCbtSppEndpointResolver;
import com.yovo.lib_bluetooth.internal.classic.service.resolve.YoCbtUuidSelector;
import com.yovo.lib_bluetooth.internal.classic.spp.channel.YoCbtSppChannelPolicy;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionManager;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Android 模块依赖图，组装并持有各蓝牙能力组件实例。
 */

public final class YoCbtAndroidModuleGraph {

    public final Context appContext;
    public final CbtInternalClientConfig clientConfig;
    public final YoCbtRuntime runtime;
    public final YoBluetoothAdapterProvider adapterProvider;
    public final YoBluetoothPermissionChecker permissionChecker;
    public final YoCbtAndroidDeviceMapper mapper;
    public final YoCbtDeviceSnapshotStore snapshotStore;
    public final YoCbtServiceCache serviceCache;
    public final YoCbtServiceChannelRegistry channelRegistry;
    public final YoCbtAndroidReceiverRegistry receiverRegistry;
    public final YoCbtReleaseCoordinator releaseCoordinator;

    public final YoCbtDiscoveryPlatform discoveryPlatform;
    public final YoCbtDiscoveryReceiverAdapter discoveryReceiverAdapter;
    public final YoCbtDiscoveryController discoveryController;
    public final YoCbtBondedDeviceRepository bondedDeviceRepository;
    public final YoCbtBondedDeviceLoader bondedDeviceLoader;
    public final YoCbtBondedDeviceCoordinator bondedDeviceCoordinator;
    public final YoCbtBondedDeviceProvider bondedDeviceProvider;
    public final YoCbtBondStateReceiverAdapter bondStateReceiver;
    public final YoCbtBondOperationController bondOperationController;
    public final YoCbtAclLinkTracker aclLinkTracker;
    public final YoCbtAclReceiverAdapter aclReceiver;
    public final YoCbtAclLinkRepository aclLinkRepository;
    public final YoCbtSdpReceiverAdapter sdpReceiverAdapter;
    public final YoCbtServiceDiscoveryClient serviceDiscoveryClient;
    public final YoCbtSppEndpointResolver sppEndpointResolver;
    public final YoCbtAndroidProfileProxyManager profileProxyManager;
    public final YoCbtA2dpProfileController a2dpProfileController;
    public final YoCbtHeadsetProfileController headsetProfileController;
    public final YoCbtProfileRegistry profileRegistry;
    public final YoCbtProfileExtensionRegistry profileExtensionRegistry;
    public final YoCbtProfileStateRepository profileStateRepository;
    public final YoCbtAndroidConnectedDeviceQuery connectedDeviceQuery;
    public final YoCbtAndroidProfileStateQuerier profileStateQuerier;
    public final YoCbtScoAudioStateTracker scoAudioStateTracker;
    public final YoCbtProfileReceiverAdapter profileReceiverAdapter;
    public final YoCbtCommandSessionRegistry commandSessionRegistry;
    public final YoCbtSppConnectionManager sppManager;

    private YoCbtAndroidModuleGraph(
            @NonNull Context appContext,
            @NonNull CbtInternalClientConfig clientConfig,
            @NonNull YoCbtRuntime runtime,
            @NonNull YoBluetoothAdapterProvider adapterProvider,
            @NonNull YoBluetoothPermissionChecker permissionChecker,
            @NonNull YoCbtAndroidDeviceMapper mapper,
            @NonNull YoCbtDeviceSnapshotStore snapshotStore,
            @NonNull YoCbtServiceCache serviceCache,
            @NonNull YoCbtServiceChannelRegistry channelRegistry,
            @NonNull YoCbtAndroidReceiverRegistry receiverRegistry,
            @NonNull YoCbtReleaseCoordinator releaseCoordinator,
            @NonNull YoCbtDiscoveryPlatform discoveryPlatform,
            @NonNull YoCbtDiscoveryReceiverAdapter discoveryReceiverAdapter,
            @NonNull YoCbtDiscoveryController discoveryController,
            @NonNull YoCbtBondedDeviceRepository bondedDeviceRepository,
            @NonNull YoCbtBondedDeviceLoader bondedDeviceLoader,
            @NonNull YoCbtBondedDeviceCoordinator bondedDeviceCoordinator,
            @NonNull YoCbtBondedDeviceProvider bondedDeviceProvider,
            @NonNull YoCbtBondStateReceiverAdapter bondStateReceiver,
            @NonNull YoCbtBondOperationController bondOperationController,
            @NonNull YoCbtAclLinkTracker aclLinkTracker,
            @NonNull YoCbtAclReceiverAdapter aclReceiver,
            @NonNull YoCbtAclLinkRepository aclLinkRepository,
            @NonNull YoCbtSdpReceiverAdapter sdpReceiverAdapter,
            @NonNull YoCbtServiceDiscoveryClient serviceDiscoveryClient,
            @NonNull YoCbtSppEndpointResolver sppEndpointResolver,
            @NonNull YoCbtAndroidProfileProxyManager profileProxyManager,
            @NonNull YoCbtA2dpProfileController a2dpProfileController,
            @NonNull YoCbtHeadsetProfileController headsetProfileController,
            @NonNull YoCbtProfileRegistry profileRegistry,
            @NonNull YoCbtProfileExtensionRegistry profileExtensionRegistry,
            @NonNull YoCbtProfileStateRepository profileStateRepository,
            @NonNull YoCbtAndroidConnectedDeviceQuery connectedDeviceQuery,
            @NonNull YoCbtAndroidProfileStateQuerier profileStateQuerier,
            @NonNull YoCbtScoAudioStateTracker scoAudioStateTracker,
            @NonNull YoCbtProfileReceiverAdapter profileReceiverAdapter,
            @NonNull YoCbtCommandSessionRegistry commandSessionRegistry,
            @NonNull YoCbtSppConnectionManager sppManager) {
        this.appContext = appContext;
        this.clientConfig = clientConfig;
        this.runtime = runtime;
        this.adapterProvider = adapterProvider;
        this.permissionChecker = permissionChecker;
        this.mapper = mapper;
        this.snapshotStore = snapshotStore;
        this.serviceCache = serviceCache;
        this.channelRegistry = channelRegistry;
        this.receiverRegistry = receiverRegistry;
        this.releaseCoordinator = releaseCoordinator;
        this.discoveryPlatform = discoveryPlatform;
        this.discoveryReceiverAdapter = discoveryReceiverAdapter;
        this.discoveryController = discoveryController;
        this.bondedDeviceRepository = bondedDeviceRepository;
        this.bondedDeviceLoader = bondedDeviceLoader;
        this.bondedDeviceCoordinator = bondedDeviceCoordinator;
        this.bondedDeviceProvider = bondedDeviceProvider;
        this.bondStateReceiver = bondStateReceiver;
        this.bondOperationController = bondOperationController;
        this.aclLinkTracker = aclLinkTracker;
        this.aclReceiver = aclReceiver;
        this.aclLinkRepository = aclLinkRepository;
        this.sdpReceiverAdapter = sdpReceiverAdapter;
        this.serviceDiscoveryClient = serviceDiscoveryClient;
        this.sppEndpointResolver = sppEndpointResolver;
        this.profileProxyManager = profileProxyManager;
        this.a2dpProfileController = a2dpProfileController;
        this.headsetProfileController = headsetProfileController;
        this.profileRegistry = profileRegistry;
        this.profileExtensionRegistry = profileExtensionRegistry;
        this.profileStateRepository = profileStateRepository;
        this.connectedDeviceQuery = connectedDeviceQuery;
        this.profileStateQuerier = profileStateQuerier;
        this.scoAudioStateTracker = scoAudioStateTracker;
        this.profileReceiverAdapter = profileReceiverAdapter;
        this.commandSessionRegistry = commandSessionRegistry;
        this.sppManager = sppManager;
    }

    @NonNull
    public static YoCbtAndroidModuleGraph create(
            @NonNull Context context,
            @NonNull CbtInternalClientConfig clientConfig,
            @NonNull YoCbtProfileReceiverAdapter.ObserverProvider profileObserverProvider) {
        Context appContext = context.getApplicationContext();
        YoCbtRuntime runtime = new YoCbtRuntime(clientConfig.getRuntimeConfig());
        YoBluetoothAdapterProvider adapterProvider = new YoBluetoothAdapterProvider(appContext);
        YoBluetoothPermissionChecker permissionChecker = new YoBluetoothPermissionChecker();
        YoCbtAndroidDeviceMapper mapper = new YoCbtAndroidDeviceMapper();
        YoCbtDeviceSnapshotStore snapshotStore = new YoCbtDeviceSnapshotStore();
        YoCbtServiceCache serviceCache = new YoCbtServiceCache();
        YoCbtServiceChannelRegistry channelRegistry = new YoCbtServiceChannelRegistry();
        YoCbtProfileUuidMapper profileUuidMapper = new YoCbtProfileUuidMapper();
        YoCbtAndroidReceiverRegistry receiverRegistry = new YoCbtAndroidReceiverRegistry();
        YoCbtReleaseCoordinator releaseCoordinator = new YoCbtReleaseCoordinator();

        YoCbtDiscoveryPlatform discoveryPlatform =
                new YoCbtAndroidDiscoveryPlatform(adapterProvider, permissionChecker);
        YoCbtDiscoveryReceiverAdapter discoveryReceiverAdapter =
                new YoCbtDiscoveryReceiverAdapter(appContext, runtime, mapper, receiverRegistry);
        YoCbtDiscoveryResultMerger discoveryMerger = new YoCbtDiscoveryResultMerger(
                snapshotStore, new YoCbtDeviceIdentityResolver());
        YoCbtDiscoveryController discoveryController = new YoCbtDiscoveryController(
                appContext, runtime, permissionChecker, discoveryPlatform,
                discoveryReceiverAdapter, discoveryMerger);

        YoCbtBondedDeviceRepository bondedDeviceRepository = new YoCbtBondedDeviceRepository(snapshotStore);
        YoCbtBondedDeviceLoader bondedDeviceLoader = new YoCbtAndroidBondedDeviceLoader(
                adapterProvider, permissionChecker, mapper);
        YoCbtBondedDeviceCoordinator bondedDeviceCoordinator = new YoCbtBondedDeviceCoordinator(
                bondedDeviceLoader, bondedDeviceRepository, snapshotStore, serviceCache,
                channelRegistry, profileUuidMapper);
        YoCbtBondedDeviceProvider bondedDeviceProvider = new YoCbtBondedDeviceProvider(
                adapterProvider, permissionChecker, bondedDeviceCoordinator);
        YoCbtBondStateReceiverAdapter bondStateReceiver = new YoCbtBondStateReceiverAdapter(
                appContext, runtime, mapper, snapshotStore, receiverRegistry);
        YoCbtBondOperationController bondOperationController = new YoCbtBondOperationController(
                permissionChecker,
                new YoCbtAndroidBondOperationPlatform(
                        adapterProvider, clientConfig.isAllowHiddenApiBondManagement()));
        YoCbtAclLinkTracker aclLinkTracker = new YoCbtAclLinkTracker(snapshotStore);
        YoCbtAclReceiverAdapter aclReceiver = new YoCbtAclReceiverAdapter(
                appContext, runtime, mapper, aclLinkTracker, receiverRegistry);
        YoCbtAclLinkRepository aclLinkRepository = new YoCbtAclLinkRepository(
                aclLinkTracker, snapshotStore);

        YoCbtSdpReceiverAdapter sdpReceiverAdapter = new YoCbtSdpReceiverAdapter(
                appContext, runtime, mapper, channelRegistry, receiverRegistry);
        YoCbtUuidSelector uuidSelector = new YoCbtUuidSelector();
        YoCbtSppEndpointResolver sppEndpointResolver = new YoCbtSppEndpointResolver();
        YoCbtSdpResolver sdpResolver = new YoCbtSdpResolver(
                profileUuidMapper, uuidSelector, sppEndpointResolver, snapshotStore, serviceCache);
        YoCbtServiceDiscoveryClient serviceDiscoveryClient = new YoCbtServiceDiscoveryClient(
                runtime, sdpReceiverAdapter, sdpResolver, serviceCache);

        YoCbtAndroidProfileProxyManager profileProxyManager = new YoCbtAndroidProfileProxyManager(
                appContext, adapterProvider.adapter());
        YoCbtAndroidA2dpProfilePort a2dpProfilePort =
                new YoCbtAndroidA2dpProfilePort(profileProxyManager);
        YoCbtAndroidHeadsetProfilePort headsetProfilePort =
                new YoCbtAndroidHeadsetProfilePort(profileProxyManager);
        YoCbtA2dpProfileController a2dpProfileController =
                new YoCbtA2dpProfileController(a2dpProfilePort);
        YoCbtHeadsetProfileController headsetProfileController =
                new YoCbtHeadsetProfileController(headsetProfilePort);
        YoCbtProfileRegistry profileRegistry = new YoCbtProfileRegistry();
        YoCbtProfileExtensionRegistry profileExtensionRegistry = new YoCbtProfileExtensionRegistry();
        YoCbtAndroidProfileExtensionBootstrap.registerAll(
                profileExtensionRegistry, appContext, adapterProvider.adapter());
        YoCbtProfileStateRepository profileStateRepository = new YoCbtProfileStateRepository(
                snapshotStore, a2dpProfileController, headsetProfileController);
        YoCbtAndroidConnectedDeviceQuery connectedDeviceQuery = new YoCbtAndroidConnectedDeviceQuery(
                appContext, adapterProvider.adapter(), runtime, mapper, snapshotStore, profileStateRepository);
        YoCbtAndroidProfileStateQuerier profileStateQuerier = new YoCbtAndroidProfileStateQuerier(
                profileStateRepository, profileExtensionRegistry);
        YoCbtScoAudioStateTracker scoAudioStateTracker = new YoCbtScoAudioStateTracker();
        YoCbtProfileBroadcastApplier profileBroadcastApplier = new YoCbtProfileBroadcastApplier(
                profileStateRepository, a2dpProfileController, headsetProfileController,
                scoAudioStateTracker, runtime.eventHub(), runtime);
        YoCbtAndroidProfileBroadcastHandler androidProfileBroadcastHandler =
                new YoCbtAndroidProfileBroadcastHandler(mapper, profileBroadcastApplier);
        YoCbtProfileReceiverAdapter profileReceiverAdapter = new YoCbtProfileReceiverAdapter(
                appContext, runtime, androidProfileBroadcastHandler, profileObserverProvider, receiverRegistry);

        YoCbtSppChannelPolicy channelPolicy = clientConfig.isAllowHiddenApiSppChannel()
                ? YoCbtSppChannelPolicy.explicitOnly()
                : YoCbtSppChannelPolicy.disabled();
        YoCbtSppAndroidSocketFactory socketFactory =
                new YoCbtSppAndroidSocketFactory(adapterProvider.adapter(), channelPolicy);
        YoCbtSppRemoteDeviceGateway remoteDeviceGateway =
                new YoCbtSppRemoteDeviceGateway(adapterProvider, mapper);
        YoCbtCommandSessionRegistry commandSessionRegistry = new YoCbtCommandSessionRegistry(runtime);
        YoCbtSppConnectionManager sppManager = new YoCbtSppConnectionManager(
                runtime, remoteDeviceGateway, socketFactory, commandSessionRegistry);

        YoCbtAndroidModuleGraph graph = new YoCbtAndroidModuleGraph(
                appContext, clientConfig, runtime, adapterProvider, permissionChecker, mapper,
                snapshotStore, serviceCache, channelRegistry, receiverRegistry, releaseCoordinator,
                discoveryPlatform, discoveryReceiverAdapter, discoveryController,
                bondedDeviceRepository, bondedDeviceLoader, bondedDeviceCoordinator,
                bondedDeviceProvider, bondStateReceiver, bondOperationController,
                aclLinkTracker, aclReceiver, aclLinkRepository, sdpReceiverAdapter,
                serviceDiscoveryClient, sppEndpointResolver, profileProxyManager,
                a2dpProfileController, headsetProfileController, profileRegistry,
                profileExtensionRegistry, profileStateRepository, connectedDeviceQuery,
                profileStateQuerier, scoAudioStateTracker, profileReceiverAdapter, commandSessionRegistry, sppManager);

        graph.registerReleaseHooks();
        return graph;
    }

    public void registerReceivers() {
        discoveryReceiverAdapter.register();
        bondStateReceiver.register();
        aclReceiver.register();
        sdpReceiverAdapter.register();
        profileReceiverAdapter.register();
    }

    private void registerReleaseHooks() {
        releaseCoordinator.register("service-cache", serviceCache::clear);
        releaseCoordinator.register("receiver-registry", () -> receiverRegistry.unregisterAll());
        releaseCoordinator.register("profile-extensions", profileExtensionRegistry::releaseAll);
        releaseCoordinator.register("profile-proxy", profileProxyManager::releaseAll);
        releaseCoordinator.register("spp", sppManager::release);
        releaseCoordinator.register("command-sessions", commandSessionRegistry::clear);
        releaseCoordinator.register("service-discovery", serviceDiscoveryClient::release);
        releaseCoordinator.register("snapshot-store", snapshotStore::clear);
        releaseCoordinator.register("profile-receiver", profileReceiverAdapter::unregister);
        releaseCoordinator.register("sdp-receiver", sdpReceiverAdapter::unregister);
        releaseCoordinator.register("discovery-receiver", discoveryReceiverAdapter::unregister);
        releaseCoordinator.register("acl-receiver", aclReceiver::unregister);
        releaseCoordinator.register("bond-receiver", bondStateReceiver::unregister);
        releaseCoordinator.register("discovery", discoveryController::release);
    }

    public void releaseAll() {
        releaseCoordinator.releaseAll();
        runtime.release();
    }
}
