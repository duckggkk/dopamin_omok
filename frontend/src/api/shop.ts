import client from './client';
import { ApiResponse, ShopInfo, GachaResult, Inventory } from '@/types';
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
};
