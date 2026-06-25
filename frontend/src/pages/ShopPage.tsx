import { useState, useEffect, useCallback } from 'react';
import { shopApi } from '@/api/shop';
import { useAuthStore } from '@/store/authStore';
import { ShopInfo, Inventory, GachaResult, ShopItem, ItemType } from '@/types';
import ItemPreview from '@/components/shop/ItemPreview';
import PreviewBoard from '@/components/shop/PreviewBoard';
import { useStoneSoundPlayer } from '@/hooks/useStoneSoundPlayer';
import { DEFAULT_BLACK_STONE } from '@/utils/stoneSkin';
import styles from './ShopPage.module.css';

// 목록 첫 칸에 보여줄 가상의 '기본'(장착 해제) 카드용 sentinel id (실제 아이템 아님)
const DEFAULT_ITEM_ID = -1;

// 기본 바둑판 외형 — OmokBoard/미리보기 폴백과 동일
const DEFAULT_BOARD_COLORS = { bg: '#dcb95b', lines: '#8b6914', dots: '#8b6914' };
const DEFAULT_BOARD_FILTER = {
  type: 'fractalNoise', freqX: 0.65, freqY: 0.06, octaves: 4, seed: 3, blend: 'overlay',
};

// 기본 지급(default_grant) 실아이템이 없는 카테고리에만 보여줄 가상 '기본' 카드(누르면 장착 해제).
// DEFEAT_MESSAGE·STONE_SOUND 는 이미 기본 지급 실아이템('패배'·'기본음')이 있어 정렬로 1순위 처리한다.
const DEFAULT_ITEMS: Partial<Record<ItemType, ShopItem>> = {
  STONE_SKIN: {
    id: DEFAULT_ITEM_ID, name: '기본 스킨', displayName: '기본 스킨', itemType: 'STONE_SKIN',
    description: '기본 바둑알입니다.', itemConfig: { displayName: '기본 스킨', stone: DEFAULT_BLACK_STONE },
  },
  BOARD_SKIN: {
    id: DEFAULT_ITEM_ID, name: '기본 스킨', displayName: '기본 스킨', itemType: 'BOARD_SKIN',
    description: '기본 바둑판입니다.',
    itemConfig: { displayName: '기본 스킨', colors: DEFAULT_BOARD_COLORS, filter: DEFAULT_BOARD_FILTER },
  },
  STONE_EFFECT: {
    id: DEFAULT_ITEM_ID, name: '일반 착수', displayName: '일반 착수', itemType: 'STONE_EFFECT',
    description: '효과 없는 기본 착수입니다.', itemConfig: { displayName: '일반 착수' },
  },
};

// 타입별 표시 메타 (아이콘/카테고리 라벨). 새 코스메틱 타입 추가 시 여기 한 줄만 추가.
const ITEM_TYPE_META: Record<ItemType, { icon: string; label: string }> = {
  DEFEAT_MESSAGE: { icon: '💬', label: '패배 문구' },
  DEFEAT_EFFECT: { icon: '🔥', label: '승패 이펙트' },
  BOARD_SKIN: { icon: '🎨', label: '바둑판 스킨' },
  STONE_SOUND: { icon: '🔊', label: '착수음' },
  STONE_SKIN: { icon: '⚫', label: '바둑알 스킨' },
  STONE_EFFECT: { icon: '✨', label: '착수 효과' },
  CHARACTER_SKIN: { icon: '🧙', label: '피지컬 캐릭터' },
};
const COMING_SOON_TYPES = new Set<ItemType>(['DEFEAT_EFFECT']);
const INVENTORY_TYPES = (Object.keys(ITEM_TYPE_META) as ItemType[])
  .filter((type) => !COMING_SOON_TYPES.has(type));

const ShopPage = () => {
  const { user, setUser } = useAuthStore();
  const playStoneSound = useStoneSoundPlayer();
  const [shopInfo, setShopInfo] = useState<ShopInfo | null>(null);
  const [inventory, setInventory] = useState<Inventory | null>(null);
  const [tab, setTab] = useState<'shop' | 'inventory'>('shop');
  const [gachaResult, setGachaResult] = useState<GachaResult | null>(null);
  const [isOpening, setIsOpening] = useState(false);
  // 뽑기 연출 단계: idle=닫힘 / opening='두근두근' 상자 흔들기 / revealed=아이템 공개
  const [gachaPhase, setGachaPhase] = useState<'idle' | 'opening' | 'revealed'>('idle');
  const [equippingId, setEquippingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  // 미리보기 모드 — 클래식 바둑판 / 피지컬 오목(캐릭터 등장) 전환
  const [previewMode, setPreviewMode] = useState<'classic' | 'physical'>('classic');
  const [resultPreviewMode, setResultPreviewMode] = useState<'win' | 'loss'>('win');
  // 미리보기 바둑판에 적용된 코스메틱 — 카테고리(itemType)별 1개씩 겹쳐서 미리보기
  const [preview, setPreview] = useState<Partial<Record<ItemType, ShopItem>>>({});

  const handlePreviewClick = (item: ShopItem) => {
    if (item.itemType === 'STONE_SOUND') {
      playStoneSound(item.itemConfig?.assetKey);
    }
    if (item.itemType === 'CHARACTER_SKIN') {
      setPreviewMode('physical');
    }
    if (item.itemType === 'DEFEAT_EFFECT') {
      setResultPreviewMode('win');
    }
    if (item.itemType === 'DEFEAT_MESSAGE') {
      setResultPreviewMode('loss');
    }
    setPreview((p) => ({ ...p, [item.itemType]: p[item.itemType]?.id === item.id ? undefined : item }));
  };
  const isPreviewing = (item: ShopItem) => preview[item.itemType]?.id === item.id;
  const hasPreview = Object.values(preview).some(Boolean);

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

  // 오류(돌 부족 등)는 화면 정중앙 토스트로 띄우고 잠시 후 자동으로 사라지게 한다(상단 작은 글씨는 잘 안 보였음).
  useEffect(() => {
    if (!error) return;
    const id = window.setTimeout(() => setError(null), 2800);
    return () => window.clearTimeout(id);
  }, [error]);

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
    if (gachaPhase !== 'idle') return;
    setError(null);
    setGachaResult(null);
    setGachaPhase('opening'); // 결과가 오기 전부터 상자 흔들기 연출 시작 → 기대감
    setIsOpening(true);
    const startedAt = Date.now();
    try {
      const res = await shopApi.openGacha(boxType);
      if (res.data.data) {
        const result = res.data.data;
        if (user) setUser({ ...user, currency: result.remainingCurrency });
        await loadData();
        // 네트워크가 빨라도 최소 1.1초는 '두근두근'을 보여줘 연출이 너무 휙 지나가지 않게 한다.
        const wait = Math.max(0, 1100 - (Date.now() - startedAt));
        window.setTimeout(() => {
          setGachaResult(result);
          setGachaPhase('revealed');
          setIsOpening(false);
        }, wait);
        return;
      }
      setGachaPhase('idle');
      setIsOpening(false);
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(msg ?? '뽑기에 실패했습니다.');
      setGachaPhase('idle');
      setIsOpening(false);
    }
  };

  const dismissGacha = () => {
    setGachaResult(null);
    setGachaPhase('idle');
  };

  const handleEquip = async (item: ShopItem) => {
    setEquippingId(item.id);
    try {
      // 기본 카드 = 해당 카테고리 장착 해제
      if (item.id === DEFAULT_ITEM_ID) {
        await shopApi.unequipItem(item.itemType);
      } else {
        await shopApi.equipItem(item.id);
      }
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
    item.id === DEFAULT_ITEM_ID
      ? !inventory?.activeItems[item.itemType]          // 장착된 아이템이 없으면 기본이 활성
      : inventory?.activeItems[item.itemType]?.id === item.id;

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

      {error && (
        <div className={styles.errorToast} role="alert" onClick={() => setError(null)}>
          <div className={styles.errorToastCard}>
            <span className={styles.errorToastIcon}>⚠️</span>
            <span className={styles.errorToastMsg}>{error}</span>
          </div>
        </div>
      )}

      <div className={styles.layout}>
        <div className={styles.mainCol}>
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
              {shopInfo.boxes.map((box) => {
                const comingSoon = COMING_SOON_TYPES.has(box.itemType);
                return (
                  <div key={box.type} className={`${styles.boxCard} ${comingSoon ? styles.boxCardComingSoon : ''}`}>
                    <div className={styles.boxIcon}>
                      {ITEM_TYPE_META[box.itemType].icon}
                    </div>
                    <h3 className={styles.boxName}>{box.name}</h3>
                    <div className={styles.boxItems}>
                      {comingSoon ? (
                        <div className={styles.comingSoonNotice}>
                          <span className={styles.comingSoonTitle}>준비중</span>
                        </div>
                      ) : (
                        <>
                          <p className={styles.boxItemsLabel}>포함 아이템 · 미리보기</p>
                          <div className={styles.previewGrid}>
                            {box.possibleItems.map((it) => (
                              <button
                                key={it.id}
                                className={`${styles.previewItem} ${isPreviewing(it) ? styles.previewItemOn : ''}`}
                                onClick={() => handlePreviewClick(it)}
                                title="미리보기에 적용"
                              >
                                <ItemPreview item={it} gacha />
                                <span className={styles.previewName}>{getItemDisplayName(it)}</span>
                              </button>
                            ))}
                          </div>
                        </>
                      )}
                    </div>
                    <div className={styles.boxPrice}>{comingSoon ? '준비중' : `🪨 ${box.price} 돌`}</div>
                    <button
                      className={styles.openBtn}
                      onClick={() => handleOpenGacha(box.type)}
                      disabled={isOpening || comingSoon}
                    >
                      {comingSoon ? '준비 중' : isOpening ? '뽑는 중...' : '뽑기!'}
                    </button>
                  </div>
                );
              })}
            </div>
          </section>

          {/* 뽑기 연출 + 결과 */}
          {gachaPhase !== 'idle' && (
            <div
              className={styles.resultOverlay}
              onClick={gachaPhase === 'revealed' ? dismissGacha : undefined}
            >
              <div className={styles.resultCard} onClick={(e) => e.stopPropagation()}>
                {gachaPhase === 'opening' || !gachaResult ? (
                  // 1단계: 결과 공개 전 — 흔들리는 상자로 기대감을 준다.
                  <div className={styles.gachaOpening}>
                    <div className={styles.gachaBox} aria-hidden="true">🎁</div>
                    <p className={styles.gachaSuspense}>두근두근…</p>
                  </div>
                ) : (
                  // 2단계: 공개 — 뽑은 아이템을 '실제 이미지'로 팡 터뜨려 보여준다.
                  <>
                    <p className={styles.resultLabel}>
                      {gachaResult.isDuplicate ? '뽑기 결과' : '✨ 새 아이템 획득! ✨'}
                    </p>
                    <div className={styles.revealStage}>
                      <span className={styles.revealRays} aria-hidden="true" />
                      <div className={styles.revealItem}>
                        <ItemPreview item={gachaResult.item} gacha />
                      </div>
                    </div>
                    <p className={styles.resultName}>{getItemDisplayName(gachaResult.item)}</p>
                    <p className={styles.resultCategory}>
                      {ITEM_TYPE_META[gachaResult.item.itemType].icon}{' '}
                      {ITEM_TYPE_META[gachaResult.item.itemType].label}
                    </p>
                    {gachaResult.isDuplicate && (
                      <p className={styles.resultDuplicate}>이미 보유한 아이템입니다.</p>
                    )}
                    <p className={styles.resultBalance}>
                      잔여 돌: 🪨 {gachaResult.remainingCurrency.toLocaleString()}
                    </p>
                    <button className={styles.resultClose} onClick={dismissGacha}>
                      확인
                    </button>
                  </>
                )}
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
                // 기본 지급(default_grant) 아이템을 앞으로 정렬(패배 문구 '패배', 착수음 '기본음' 등)
                const owned = inventory.items
                  .filter((i) => i.itemType === type)
                  .sort((a, b) => Number(b.defaultGrant) - Number(a.defaultGrant));
                // 기본 지급 실아이템이 없는 카테고리는 가상 '기본' 카드를 맨 앞에 둔다
                const def = DEFAULT_ITEMS[type];
                const typeItems = def ? [def, ...owned] : owned;
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
                            <button
                              className={`${styles.itemPreview} ${isPreviewing(item) ? styles.itemPreviewOn : ''}`}
                              onClick={() => handlePreviewClick(item)}
                              title="미리보기에 적용"
                            >
                              <ItemPreview item={item} />
                            </button>
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

        <aside className={styles.previewPanel}>
          <h3 className={styles.previewTitle}>🔍 미리보기</h3>
          <div className={styles.previewModeTabs}>
            <button
              className={previewMode === 'classic' ? styles.previewModeOn : styles.previewModeTab}
              onClick={() => setPreviewMode('classic')}
            >
              일반 오목
            </button>
            <button
              className={previewMode === 'physical' ? styles.previewModeOn : styles.previewModeTab}
              onClick={() => setPreviewMode('physical')}
            >
              피지컬 오목
            </button>
          </div>
          {preview.DEFEAT_EFFECT && (
            <div className={styles.resultPreviewTabs} aria-label="승패 이펙트 미리보기 화면 선택">
              <span className={styles.resultPreviewLabel}>승패 이펙트</span>
              <button
                className={resultPreviewMode === 'win' ? styles.previewModeOn : styles.previewModeTab}
                onClick={() => setResultPreviewMode('win')}
              >
                승리 화면
              </button>
              <button
                className={resultPreviewMode === 'loss' ? styles.previewModeOn : styles.previewModeTab}
                onClick={() => setResultPreviewMode('loss')}
              >
                패배 화면
              </button>
            </div>
          )}
          <PreviewBoard
            variant={previewMode}
            boardCfg={preview.BOARD_SKIN?.itemConfig ?? null}
            stoneStyle={preview.STONE_SKIN?.itemConfig?.stone ?? null}
            effect={preview.STONE_EFFECT?.itemConfig?.effect ?? null}
            soundKey={preview.STONE_SOUND?.itemConfig?.assetKey ?? null}
            character={preview.CHARACTER_SKIN?.itemConfig?.character ?? null}
            defeatText={preview.DEFEAT_MESSAGE ? getItemDisplayName(preview.DEFEAT_MESSAGE) : null}
            defeatEffect={preview.DEFEAT_EFFECT?.itemConfig?.effect ?? null}
            resultPreview={preview.DEFEAT_EFFECT ? resultPreviewMode : 'loss'}
          />
          <p className={styles.previewHint}>
            {previewMode === 'physical'
              ? '피지컬 오목 미리보기 — 캐릭터·바둑알·착수 효과가 실시간 액션 모드에서 어떻게 보이는지 확인하세요.'
              : '상품을 클릭해 적용하고, 바둑판을 직접 둬보며 스킨·착수음·효과를 확인하세요. 카테고리별로 1개씩 겹쳐 볼 수 있어요.'}
          </p>
          {hasPreview && (
            <button className={styles.previewReset} onClick={() => setPreview({})}>선택 초기화</button>
          )}
        </aside>
      </div>
    </div>
  );
};

export default ShopPage;
