import client from './client';
import { ApiResponse, ShopInfo, GachaResult, Inventory, ItemType } from '@/types';
import { AxiosResponse } from 'axios';

export const shopApi = {
  getShopInfo: (): Promise<AxiosResponse<ApiResponse<ShopInfo>>> =>
    client.get('/shop'),

  chargeCurrency: (packageId: string): Promise<AxiosResponse<ApiResponse<{ currency: number }>>> =>
    client.post('/shop/currency/charge', { packageId }),

  openGacha: (boxType: string): Promise<AxiosResponse<ApiResponse<GachaResult>>> =>
    client.post('/shop/gacha/open', { boxType }),

  getInventory: (): Promise<AxiosResponse<ApiResponse<Inventory>>> =>
    client.get('/shop/inventory'),

  equipItem: (itemId: number): Promise<AxiosResponse<ApiResponse<null>>> =>
    client.post('/shop/equip', { itemId }),

  // 장착 해제 — 해당 카테고리를 기본값으로 되돌림(예: 바둑알 스킨 → 기본 스킨)
  unequipItem: (itemType: ItemType): Promise<AxiosResponse<ApiResponse<null>>> =>
    client.post('/shop/unequip', { itemType }),
};