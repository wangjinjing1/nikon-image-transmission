import type { CameraProfile } from './types';

export const cameraProfiles: CameraProfile[] = [
  {
    id: 'Z30',
    name: 'Nikon Z30',
    supportedModes: ['ap'],
    defaultHost: '192.168.1.1',
    notes: '支持相机 Wi-Fi AP 直连，手机连接相机热点后传输。'
  },
  {
    id: 'Z5II',
    name: 'Nikon Z5II',
    supportedModes: ['ap', 'sta'],
    defaultHost: '192.168.1.1',
    notes: '支持 AP 直连，也支持 STA 模式接入同一无线网络。'
  }
];

export const downloadSizeLabel = {
  '2mp': '2MP',
  '8mp': '8MP',
  original: '原始格式'
} as const;
