package com.soomla.cocos2dx.store;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import com.soomla.cocos2dx.common.DomainFactory;
import com.soomla.cocos2dx.common.NdkGlue;
import com.soomla.store.*;
import com.soomla.store.billing.google.GooglePlayIabService;
import com.soomla.store.data.StoreInfo;
import com.soomla.store.domain.*;
import com.soomla.store.domain.virtualCurrencies.VirtualCurrency;
import com.soomla.store.domain.virtualCurrencies.VirtualCurrencyPack;
import com.soomla.store.domain.virtualGoods.*;
import com.soomla.store.exceptions.InsufficientFundsException;
import com.soomla.store.exceptions.NotEnoughGoodsException;
import com.soomla.store.exceptions.VirtualItemNotFoundException;
import com.soomla.store.purchaseTypes.PurchaseWithMarket;
import org.cocos2dx.lib.Cocos2dxGLSurfaceView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * @author vedi
 *         date 6/10/14
 *         time 11:08 AM
 */
public class StoreService {

    private static StoreService INSTANCE = null;

    private static WeakReference<GLSurfaceView> glSurfaceViewRef = new WeakReference<GLSurfaceView>(null);
    private static String mPublicKey           = "";
    private static IStoreAssets mStoreAssets   = null;
    private boolean inited = false;

    public static StoreService getInstance() {
        if (INSTANCE == null) {
            synchronized (StoreService.class) {
                if (INSTANCE == null) {
                    INSTANCE = new StoreService();
                }
            }
        }
        return INSTANCE;
    }

    @SuppressWarnings("FieldCanBeLocal")
    private StoreEventHandlerBridge storeEventHandlerBridge;

    public StoreService() {
        storeEventHandlerBridge = new StoreEventHandlerBridge();

        final DomainFactory domainFactory = DomainFactory.getInstance();

        // Skip StoreConsts.JSON_JSON_TYPE_VIRTUAL_ITEM as abstract

        domainFactory.registerCreator(StoreConsts.JSON_JSON_TYPE_MARKET_ITEM, new DomainFactory.Creator<MarketItem>() {
            @Override
            public MarketItem create(JSONObject jsonObject) {
                try {
                    return new MarketItem(jsonObject);
                } catch (JSONException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
        domainFactory.registerCreator(StoreConsts.JSON_JSON_TYPE_NON_CONSUMABLE_ITEM, new DomainFactory.Creator<NonConsumableItem>() {
            @Override
            public NonConsumableItem create(JSONObject jsonObject) {
                try {
                    return new NonConsumableItem(jsonObject);
                } catch (JSONException e) {
                    throw new IllegalStateException(e);
                }
            }
        });

        // Skip StoreConsts.JSON_JSON_TYPE_PURCHASABLE_VIRTUAL_ITEM as abstract

        domainFactory.registerCreator(StoreConsts.JSON_JSON_TYPE_VIRTUAL_CATEGORY, new DomainFactory.Creator<VirtualCategory>() {
            @Override
            public VirtualCategory create(JSONObject jsonObject) {
                try {
                    return new VirtualCategory(jsonObject);
                } catch (JSONException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
        domainFactory.registerCreator(StoreConsts.JSON_JSON_TYPE_VIRTUAL_CURRENCY, new DomainFactory.Creator<VirtualCurrency>() {
            @Override
            public VirtualCurrency create(JSONObject jsonObject) {
                try {
                    return new VirtualCurrency(jsonObject);
                } catch (JSONException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
        domainFactory.registerCreator(StoreConsts.JSON_JSON_TYPE_VIRTUAL_CURRENCY_PACK, new DomainFactory.Creator<VirtualCurrencyPack>() {
            @Override
            public VirtualCurrencyPack create(JSONObject jsonObject) {
                try {
                    return new VirtualCurrencyPack(jsonObject);
                } catch (JSONException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
        domainFactory.registerCreator(StoreConsts.JSON_JSON_TYPE_EQUIPPABLE_VG, new DomainFactory.Creator<EquippableVG>() {
            @Override
            public EquippableVG create(JSONObject jsonObject) {
                try {
                    return new EquippableVG(jsonObject);
                } catch (JSONException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
        domainFactory.registerCreator(StoreConsts.JSON_JSON_TYPE_LIFETIME_VG, new DomainFactory.Creator<LifetimeVG>() {
            @Override
            public LifetimeVG create(JSONObject jsonObject) {
                try {
                    return new LifetimeVG(jsonObject);
                } catch (JSONException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
        domainFactory.registerCreator(StoreConsts.JSON_JSON_TYPE_SINGLE_USE_PACK_VG, new DomainFactory.Creator<SingleUsePackVG>() {
            @Override
            public SingleUsePackVG create(JSONObject jsonObject) {
                try {
                    return new SingleUsePackVG(jsonObject);
                } catch (JSONException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
        domainFactory.registerCreator(StoreConsts.JSON_JSON_TYPE_SINGLE_USE_VG, new DomainFactory.Creator<SingleUseVG>() {
            @Override
            public SingleUseVG create(JSONObject jsonObject) {
                try {
                    return new SingleUseVG(jsonObject);
                } catch (JSONException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
        domainFactory.registerCreator(StoreConsts.JSON_JSON_TYPE_UPGRADE_VG, new DomainFactory.Creator<UpgradeVG>() {
            @Override
            public UpgradeVG create(JSONObject jsonObject) {
                try {
                    return new UpgradeVG(jsonObject);
                } catch (JSONException e) {
                    throw new IllegalStateException(e);
                }
            }
        });

        // Skip StoreConsts.JSON_JSON_TYPE_VIRTUAL_GOOD as abstract

        final NdkGlue ndkGlue = NdkGlue.getInstance();

        ndkGlue.registerCallHandler("CCStoreAssets::init", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                int version = params.getInt("version");
                JSONObject storeAssetsJson = params.getJSONObject("storeAssets");
                mStoreAssets = new StoreAssetsBridge(version, storeAssetsJson);
            }
        });

        ndkGlue.registerCallHandler("CCStoreService::init", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                String customSecret = params.getString("customSecret");
                StoreUtils.LogDebug("SOOMLA", "initialize is called from java!");
                StoreController.getInstance().initialize(mStoreAssets, customSecret);
                if (StoreController.getInstance().getInAppBillingService() instanceof GooglePlayIabService) {
                    ((GooglePlayIabService) StoreController.getInstance().getInAppBillingService()).setPublicKey(mPublicKey);
                }
            }
        });

        ndkGlue.registerCallHandler("CCStoreController::buyMarketItem", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                String productId = params.getString("productId");
                String payload = params.getString("payload");
                StoreUtils.LogDebug("SOOMLA", "buyWithMarket is called from java with productId: " + productId + "!");
                PurchasableVirtualItem pvi = StoreInfo.getPurchasableItem(productId);
                if(pvi.getPurchaseType() instanceof PurchaseWithMarket) {
                    StoreController.getInstance().buyWithMarket(((PurchaseWithMarket)pvi.getPurchaseType()).getMarketItem(), payload);
                } else {
                    throw new VirtualItemNotFoundException("productId", productId);
                }
            }
        });

        ndkGlue.registerCallHandler("CCStoreController::startIabServiceInBg", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                StoreUtils.LogDebug("SOOMLA", "startIabServiceInBg is called from java!");
                StoreController.getInstance().startIabServiceInBg();
            }
        });

        ndkGlue.registerCallHandler("CCStoreController::stopIabServiceInBg", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                StoreUtils.LogDebug("SOOMLA", "stopIabServiceInBg is called from java!");
                StoreController.getInstance().stopIabServiceInBg();
            }
        });

        ndkGlue.registerCallHandler("CCStoreController::restoreTransactions", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                StoreUtils.LogDebug("SOOMLA", "restoreTransactions is called from java!");
                StoreController.getInstance().restoreTransactions();
            }
        });

        ndkGlue.registerCallHandler("CCStoreController::transactionsAlreadyRestored", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                throw new UnsupportedOperationException("transactionsAlreadyRestored has no use in Android");
            }
        });

        ndkGlue.registerCallHandler("CCStoreController::refreshInventory", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                StoreUtils.LogDebug("SOOMLA", "refreshInventory is called from java!");
                StoreController.getInstance().refreshInventory();
            }
        });

        ndkGlue.registerCallHandler("CCStoreController::setSoomSec", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                String soomSec = params.getString("soomSec");
                StoreUtils.LogDebug("SOOMLA", "setSoomSec is called from java!");
                StoreConfig.SOOM_SEC = soomSec;
            }
        });

        ndkGlue.registerCallHandler("CCStoreController::setAndroidPublicKey", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                mPublicKey = params.getString("androidPublicKey");
            }
        });

        ndkGlue.registerCallHandler("CCStoreInventory::buyItem", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                String itemId = params.getString("itemId");
                StoreUtils.LogDebug("SOOMLA", "buy is called from java!");
                StoreInventory.buy(itemId);
            }
        });

        ndkGlue.registerCallHandler("CCStoreInventory::getItemBalance", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                String itemId = params.getString("itemId");
                StoreUtils.LogDebug("SOOMLA", "getCurrencyBalance is called from java!");
                int retValue = StoreInventory.getVirtualItemBalance(itemId);
                retParams.put("return", retValue);
            }
        });

        ndkGlue.registerCallHandler("CCStoreInventory::giveItem", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                String itemId = params.getString("itemId");
                Integer amount = params.getInt("amount");
                StoreUtils.LogDebug("SOOMLA", "addCurrencyAmount is called from java!");
                StoreInventory.giveVirtualItem(itemId, amount);
            }
        });

        ndkGlue.registerCallHandler("CCStoreInventory::takeItem", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                String itemId = params.getString("itemId");
                Integer amount = params.getInt("amount");
                StoreUtils.LogDebug("SOOMLA", "removeCurrencyAmount is called from java!");
                StoreInventory.takeVirtualItem(itemId, amount);
            }
        });

        ndkGlue.registerCallHandler("CCStoreInventory::equipVirtualGood", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                String itemId = params.getString("itemId");
                StoreUtils.LogDebug("SOOMLA", "equipVirtualGood is called from java!");
                StoreInventory.equipVirtualGood(itemId);
            }
        });

        ndkGlue.registerCallHandler("CCStoreInventory::unEquipVirtualGood", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                String itemId = params.getString("itemId");
                StoreUtils.LogDebug("SOOMLA", "unEquipVirtualGood is called from java!");
                StoreInventory.unEquipVirtualGood(itemId);
            }
        });

        ndkGlue.registerCallHandler("CCStoreInventory::isVirtualGoodEquipped", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                String itemId = params.getString("itemId");
                StoreUtils.LogDebug("SOOMLA", "isVirtualGoodEquipped is called from java!");
                boolean retValue = StoreInventory.isVirtualGoodEquipped(itemId);
                retParams.put("return", retValue);
            }
        });

        ndkGlue.registerCallHandler("CCStoreInventory::getGoodUpgradeLevel", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                String goodItemId = params.getString("goodItemId");
                StoreUtils.LogDebug("SOOMLA", "getGoodUpgradeLevel is called from java!");
                Integer retValue = StoreInventory.getGoodUpgradeLevel(goodItemId);
                retParams.put("return", retValue);
            }
        });

        ndkGlue.registerCallHandler("CCStoreInventory::getGoodCurrentUpgrade", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                String goodItemId = params.getString("goodItemId");
                StoreUtils.LogDebug("SOOMLA", "removeGoodAmount is called from java!");
                String retValue = StoreInventory.getGoodCurrentUpgrade(goodItemId);
                retParams.put("return", retValue);
            }
        });

        ndkGlue.registerCallHandler("CCStoreInventory::upgradeGood", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                String goodItemId = params.getString("goodItemId");
                StoreUtils.LogDebug("SOOMLA", "upgradeVirtualGood is called from java!");
                StoreInventory.upgradeVirtualGood(goodItemId);
            }
        });

        ndkGlue.registerCallHandler("CCStoreInventory::removeGoodUpgrades", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                String goodItemId = params.getString("goodItemId");
                StoreUtils.LogDebug("SOOMLA", "removeUpgrades is called from java!");
                StoreInventory.removeUpgrades(goodItemId);
            }
        });

        ndkGlue.registerCallHandler("CCStoreInventory::nonConsumableItemExists", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                String nonConsItemId = params.getString("nonConsItemId");
                StoreUtils.LogDebug("SOOMLA", "nonConsumableItemExists is called from java!");
                boolean retValue = StoreInventory.nonConsumableItemExists(nonConsItemId);
                retParams.put("return", retValue);
            }
        });

        ndkGlue.registerCallHandler("CCStoreInventory::addNonConsumableItem", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                String nonConsItemId = params.getString("nonConsItemId");
                StoreUtils.LogDebug("SOOMLA", "addNonConsumableItem is called from java!");
                StoreInventory.addNonConsumableItem(nonConsItemId);
            }
        });

        ndkGlue.registerCallHandler("CCStoreInventory::removeNonConsumableItem", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                String nonConsItemId = params.getString("nonConsItemId");
                StoreUtils.LogDebug("SOOMLA", "removeNonConsumableItem is called from java!");
                StoreInventory.removeNonConsumableItem(nonConsItemId);
            }
        });

        ndkGlue.registerCallHandler("CCStoreInfo::getItemByItemId", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                String itemId = params.getString("itemId");
                VirtualItem virtualItem = StoreInfo.getVirtualItem(itemId);

                JSONObject retValue = new JSONObject();
                retValue.put("item", virtualItem.toJSONObject());
                retValue.put("className", virtualItem.getClass().getSimpleName());

                retParams.put("return", retValue);
            }
        });

        ndkGlue.registerCallHandler("CCStoreInfo::getPurchasableItemWithProductId", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                String productId = params.getString("productId");
                PurchasableVirtualItem purchasableVirtualItem = StoreInfo.getPurchasableItem(productId);

                JSONObject retValue = new JSONObject();
                retValue.put("item", purchasableVirtualItem.toJSONObject());
                retValue.put("className", purchasableVirtualItem.getClass().getSimpleName());
                retParams.put("return", retValue);
            }
        });

        ndkGlue.registerCallHandler("CCStoreInfo::getCategoryForVirtualGood", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                String goodItemId = params.getString("goodItemId");
                JSONObject retValue = StoreInfo.getCategory(goodItemId).toJSONObject();
                retParams.put("return", retValue);
            }
        });

        ndkGlue.registerCallHandler("CCStoreInfo::getFirstUpgradeForVirtualGood", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                String goodItemId = params.getString("goodItemId");
                UpgradeVG upgradeVG = StoreInfo.getGoodFirstUpgrade(goodItemId);
                JSONObject retValue = new JSONObject();
                retValue.put("item", upgradeVG.toJSONObject());
                retValue.put("className", upgradeVG.getClass().getSimpleName());
                retParams.put("return", retValue);
            }
        });

        ndkGlue.registerCallHandler("CCStoreInfo::getLastUpgradeForVirtualGood", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                String goodItemId = params.getString("goodItemId");
                UpgradeVG upgradeVG = StoreInfo.getGoodLastUpgrade(goodItemId);
                JSONObject retValue = new JSONObject();
                retValue.put("item", upgradeVG.toJSONObject());
                retValue.put("className", upgradeVG.getClass().getSimpleName());
                retParams.put("return", retValue);
            }
        });

        ndkGlue.registerCallHandler("CCStoreInfo::getUpgradesForVirtualGood", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                String goodItemId = params.getString("goodItemId");
                List<JSONObject> ret = new ArrayList<JSONObject>();
                List<UpgradeVG> upgradeVGs = StoreInfo.getGoodUpgrades(goodItemId);
                for (UpgradeVG upgradeVG : upgradeVGs) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("item", upgradeVG.toJSONObject());
                    jsonObject.put("className", upgradeVG.getClass().getSimpleName());
                    ret.add(jsonObject);
                }
                JSONArray retValue = new JSONArray(ret);
                retParams.put("return", retValue);
            }
        });

        ndkGlue.registerCallHandler("CCStoreInfo::getVirtualCurrencies", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                List<JSONObject> ret = new ArrayList<JSONObject>();
                List<VirtualCurrency> virtualCurrencies = StoreInfo.getCurrencies();
                for (VirtualCurrency virtualCurrency : virtualCurrencies) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("item", virtualCurrency.toJSONObject());
                    jsonObject.put("className", virtualCurrency.getClass().getSimpleName());
                    ret.add(jsonObject);
                }
                JSONArray retValue = new JSONArray(ret);
                retParams.put("return", retValue);
            }
        });

        ndkGlue.registerCallHandler("CCStoreInfo::getVirtualGoods", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                List<JSONObject> ret = new ArrayList<JSONObject>();
                List<VirtualGood> virtualGoods = StoreInfo.getGoods();
                for (VirtualGood virtualGood : virtualGoods) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("item", virtualGood.toJSONObject());
                    jsonObject.put("className", virtualGood.getClass().getSimpleName());
                    ret.add(jsonObject);
                }
                JSONArray retValue = new JSONArray(ret);
                retParams.put("return", retValue);
            }
        });

        ndkGlue.registerCallHandler("CCStoreInfo::getVirtualCurrencyPacks", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                List<JSONObject> ret = new ArrayList<JSONObject>();
                List<VirtualCurrencyPack> virtualCurrencyPacks = StoreInfo.getCurrencyPacks();
                for (VirtualCurrencyPack virtualCurrencyPack : virtualCurrencyPacks) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("item", virtualCurrencyPack.toJSONObject());
                    jsonObject.put("className", virtualCurrencyPack.getClass().getSimpleName());
                    ret.add(jsonObject);
                }
                JSONArray retValue = new JSONArray(ret);
                retParams.put("return", retValue);
            }
        });

        ndkGlue.registerCallHandler("CCStoreInfo::getNonConsumableItems", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                List<JSONObject> ret = new ArrayList<JSONObject>();
                List<NonConsumableItem> nonConsumableItems = StoreInfo.getNonConsumableItems();
                for (NonConsumableItem nonConsumableItem : nonConsumableItems) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("item", nonConsumableItem.toJSONObject());
                    jsonObject.put("className", nonConsumableItem.getClass().getSimpleName());
                    ret.add(jsonObject);
                }
                JSONArray retValue = new JSONArray(ret);
                retParams.put("return", retValue);
            }
        });

        ndkGlue.registerCallHandler("CCStoreInfo::getVirtualCategories", new NdkGlue.CallHandler() {
            @Override
            public void handle(JSONObject params, JSONObject retParams) throws Exception {
                List<JSONObject> ret = new ArrayList<JSONObject>();
                List<VirtualCategory> virtualCategories = StoreInfo.getCategories();
                for (VirtualCategory virtualCategory : virtualCategories) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("item", virtualCategory.toJSONObject());
                    jsonObject.put("className", virtualCategory.getClass().getSimpleName());
                    ret.add(jsonObject);
                }
                JSONArray retValue = new JSONArray(ret);
                retParams.put("return", retValue);
            }
        });

        final NdkGlue.ExceptionHandler exceptionHandler = new NdkGlue.ExceptionHandler() {
            @Override
            public void handle(Exception exception, JSONObject params, JSONObject retParams) throws Exception {
                retParams.put("errorInfo", exception.getClass().getName());
            }
        };

        ndkGlue.registerExceptionHandler(VirtualItemNotFoundException.class.getName(), exceptionHandler);
        ndkGlue.registerExceptionHandler(InsufficientFundsException.class.getName(), exceptionHandler);
        ndkGlue.registerExceptionHandler(NotEnoughGoodsException.class.getName(), exceptionHandler);
    }

    public void init() {
        final GLSurfaceView glSurfaceView = glSurfaceViewRef.get();
        if (glSurfaceView != null) {
            storeEventHandlerBridge.setGlSurfaceView(glSurfaceView);
        }

        inited = true;
    }

    public void setActivity(Activity activity) {
        NdkGlue.getInstance().setActivity(activity);
    }

    public void setGlSurfaceView(Cocos2dxGLSurfaceView glSurfaceView) {
        if (inited) {
            storeEventHandlerBridge.setGlSurfaceView(glSurfaceView);
        } else {
            glSurfaceViewRef = new WeakReference<GLSurfaceView>(glSurfaceView);
        }
    }
}