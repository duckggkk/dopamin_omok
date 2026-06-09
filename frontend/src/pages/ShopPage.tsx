import { useState, useEffect, useCallback } from 'react';
import { shopApi } from '@/api/shop';
import { useAuthStore } from '@/store/authStore';
import { ShopInfo, Inventory, GachaResult, ShopItem, ItemType } from '@/types';
import styles from './ShopPage.module.css';

// 타입별 표시 메타 (아이콘/카테고리 라벨). 새 코스메틱 타입 추가 시 여기 한 줄만 추가.
const ITEM_TYPE_META: Record<ItemType, { icon: string; label: string }> = {
  DEFEAT_MESSAGE: { icon: '💬', label: '패배 문구' },
  BOARD_SKIN: { icon: '🎨', label: '바둑판 스킨' },
  STONE_SOUND: { icon: '🔊', label: '착수음' },
  STONE_SKIN: { icon: '⚫', label: '바둑알 스킨' },
  STONE_EFFECT: { icon: '✨', label: '착수 효과' },
  CHARACTER_SKIN: { icon: '🧙', label: '피지컬 캐릭터' },
};
const INVENTORY_TYPES = Object.keys(ITEM_TYPE_META) as ItemType[];

const ShopPage = () => {
  const { user, setUser } = useAuthStore();
  const [shopInfo, setShopInfo] = useState<ShopInfo | null>(null);
  const [inventory, setInventory] = useState<Inventory | null>(null);
  const [tab, setTab] = useState<'shop' | 'inventory'>('shop');
  const [gachaResult, setGachaResult] = useState<GachaResult | null>(null);
  const [isOpening, setIsOpening] = useState(false);
  const [equippingId, setEquippingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    try {
      const [shopRes, invRes] = await Promise.all([
        shopApi.getShopInfo(),
        shopApi.getInventory(),
      ]);
      if (shopRes.data.data) setShopInfo(shopRes.data.data);
      if (invRes.data.data) setInventory(invRes.data.data);
    } catch {
      setError('데이터를 불러오지 못했습니다.');
    }
  }, []);

  useEffect(() => { loadData(); }, [loadData]);

  const handleCharge = async (packageId: string) => {
    try {
      const res = await shopApi.chargeCurrency(packageId);
      if (res.data.data) {
        const newCurrency = res.data.data.currency;
        if (user) setUser({ ...user, currency: newCurrency });
        setInventory((prev) => prev ? { ...prev, currency: newCurrency } : prev);
      }
    } catch {
      setError('충전에 실패했습니다.');
    }
  };

  const handleOpenGacha = async (boxType: string) => {
    setIsOpening(true);
    setGachaResult(null);
    setError(null);
    try {
      const res = await shopApi.openGacha(boxType);
      if (res.data.data) {
        const result = res.data.data;
        setGachaResult(result);
        if (user) setUser({ ...user, currency: result.remainingCurrency });
        await loadData();
      }
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(msg ?? '뽑기에 실패했습니다.');
    } finally {
      setIsOpening(false);
    }
  };

  const handleEquip = async (item: ShopItem) => {
    setEquippingId(item.id);
    try {
      await shopApi.equipItem(item.id);
      await loadData();
    } catch {
      setError('장착에 실패했습니다.');
    } finally {
      setEquippingId(null);
    }
  };

  // 표시명은 백엔드(item.displayName)에서 제공 — 프론트 하드코딩 없음
  const getItemDisplayName = (item: ShopItem) => item.displayName ?? item.name;

  const isEquipped = (item: ShopItem) =>
    inventory?.activeItems[item.itemType]?.id === item.id;

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <h1 className={styles.title}>상점</h1>
        <div className={styles.balance}>
          🪨 <span>{(inventory?.currency ?? user?.currency ?? 0).toLocaleString()}</span> 돌
        </div>
      </div>

      <div className={styles.tabs}>
        <button
          className={tab === 'shop' ? styles.tabActive : styles.tab}
          onClick={() => setTab('shop')}
        >
          상점
        </button>
        <button
          className={tab === 'inventory' ? styles.tabActive : styles.tab}
          onClick={() => setTab('inventory')}
        >
          보유 아이템
        </button>
      </div>

      {error && <p className={styles.error}>{error}</p>}

      {tab === 'shop' && shopInfo && (
        <div className={styles.shopContent}>
          {/* 돌 충전 */}
          <section className={styles.section}>
            <h2 className={styles.sectionTitle}>🪨 돌 충전</h2>
            <p className={styles.sectionDesc}>실제 결제 없이 바로 충전됩니다. (10,000원 = 100돌 기준)</p>
            <div className={styles.packageGrid}>
              {shopInfo.packages.map((pkg) => (
                <div key={pkg.id} className={styles.packageCard}>
                  <div className={styles.packageCurrency}>🪨 {pkg.currency.toLocaleString()}</div>
                  <div className={styles.packageLabel}>{pkg.label}</div>
                  {pkg.currency >= 550 && (
                    <div className={styles.packageBonus}>
                      +{Math.round((pkg.currency / (pkg.priceKrw / 100) - 1) * 100)}% 보너스
                    </div>
                  )}
                  <div className={styles.packagePrice}>{pkg.priceKrw.toLocaleString()}원</div>
                  <button
                    className={styles.chargeBtn}
                    onClick={() => handleCharge(pkg.id)}
                  >
                    충전
                  </button>
                </div>
              ))}
            </div>
          </section>

          {/* 뽑기 상자 */}
          <section className={styles.section}>
            <h2 className={styles.sectionTitle}>🎲 랜덤 뽑기 상자</h2>
            <div className={styles.boxGrid}>
              {shopInfo.boxes.map((box) => (
                <div key={box.type} className={styles.boxCard}>
                  <div className={styles.boxIcon}>
                    {ITEM_TYPE_META[box.itemType].icon}
                  </div>
                  <h3 className={styles.boxName}>{box.name}</h3>
                  <div className={styles.boxItems}>
                    <p className={styles.boxItemsLabel}>포함 아이템</p>
                    <div className={styles.boxItemTags}>
                      {box.possibleItems.map((name) => (
                        <span key={name} className={styles.boxItemTag}>{name}</span>
                      ))}
                    </div>
                  </div>
                  <div className={styles.boxPrice}>🪨 {box.price} 돌</div>
                  <button
                    className={styles.openBtn}
                    onClick={() => handleOpenGacha(box.type)}
                    disabled={isOpening}
                  >
                    {isOpening ? '뽑는 중...' : '뽑기!'}
                  </button>
                </div>
              ))}
            </div>
          </section>

          {/* 뽑기 결과 */}
          {gachaResult && (
            <div className={styles.resultOverlay} onClick={() => setGachaResult(null)}>
              <div className={styles.resultCard} onClick={(e) => e.stopPropagation()}>
                <p className={styles.resultLabel}>뽑기 결과!</p>
                <div className={styles.resultItem}>
                  {ITEM_TYPE_META[gachaResult.item.itemType].icon}
                </div>
                <p className={styles.resultName}>
                  {getItemDisplayName(gachaResult.item)}
                </p>
                {gachaResult.isDuplicate && (
                  <p className={styles.resultDuplicate}>이미 보유한 아이템입니다.</p>
                )}
                <p className={styles.resultBalance}>
                  잔여 돌: 🪨 {gachaResult.remainingCurrency.toLocaleString()}
                </p>
                <button className={styles.resultClose} onClick={() => setGachaResult(null)}>
                  확인
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {tab === 'inventory' && (
        <div className={styles.inventoryContent}>
          {!inventory || inventory.items.length === 0 ? (
            <div className={styles.emptyInventory}>
              <p>보유한 아이템이 없습니다.</p>
              <p className={styles.emptyHint}>상점에서 뽑기를 해보세요!</p>
            </div>
          ) : (
            <>
              {INVENTORY_TYPES.map((type) => {
                const typeItems = inventory.items.filter((i) => i.itemType === type);
                if (typeItems.length === 0) return null;
                return (
                  <section key={type} className={styles.section}>
                    <h2 className={styles.sectionTitle}>
                      {ITEM_TYPE_META[type].icon} {ITEM_TYPE_META[type].label}
                    </h2>
                    <div className={styles.itemGrid}>
                      {typeItems.map((item) => {
                        const equipped = isEquipped(item);
                        return (
                          <div key={item.id} className={`${styles.itemCard} ${equipped ? styles.itemCardEquipped : ''}`}>
                            <div className={styles.itemIcon}>
                              {ITEM_TYPE_META[item.itemType].icon}
                            </div>
                            <p className={styles.itemName}>{getItemDisplayName(item)}</p>
                            {equipped ? (
                              <span className={styles.equippedBadge}>장착 중</span>
                            ) : (
                              <button
                                className={styles.equipBtn}
                                onClick={() => handleEquip(item)}
                                disabled={equippingId === item.id}
                              >
                                {equippingId === item.id ? '...' : '장착'}
                              </button>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  </section>
                );
              })}
            </>
          )}
        </div>
      )}
    </div>
  );
};

export default ShopPage;
