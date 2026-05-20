import type { CameraProfile } from './types';

export const cameraProfiles: CameraProfile[] = [
  {
    id: 'Z30',
    name: 'Nikon Z30',
    supportedModes: ['ap'],
    defaultHost: '',
    imageUrl: 'https://imaging.nikon.com/imaging/lineup/mirrorless/z_30/img/product_01.png',
    notes: '支持相机 Wi-Fi AP 直连，手机连接相机热点后传输。'
  },
  {
    id: 'Z5II',
    name: 'Nikon Z5II',
    supportedModes: ['ap', 'sta'],
    defaultHost: '',
    imageUrl: 'https://imaging.nikon.com/imaging/lineup/mirrorless/z_5_2/img/product_01.png',
    notes: '支持 AP 直连，也支持 STA 模式接入手机热点或同一无线网络，APP 会自动发现相机地址。'
  }
];

export const downloadSizeLabel = {
  '2mp': '2MP',
  '8mp': '8MP',
  original: '原始格式'
} as const;
