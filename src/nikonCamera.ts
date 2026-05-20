import { Capacitor, registerPlugin } from '@capacitor/core';
import type { CameraConnection, CameraModel, CameraPhoto, ConnectionMode, DownloadSize } from './types';

interface NikonCameraPlugin {
  connect(options: { model: CameraModel; mode: ConnectionMode; host: string; wifiPassword?: string; port?: number }): Promise<CameraConnection>;
  listPhotos(): Promise<{ photos: CameraPhoto[] }>;
  requestStoragePermission(): Promise<{ storage: 'granted' | 'denied' | 'prompt' | 'prompt-with-rationale' }>;
  downloadPhotos(options: { objectHandles: number[]; size: DownloadSize; albumName: string }): Promise<{ saved: number }>;
  disconnect(): Promise<{ connected: boolean }>;
}

const NativeNikonCamera = registerPlugin<NikonCameraPlugin>('NikonCamera');

const demoPhotos: CameraPhoto[] = [
  {
    id: 'demo-1',
    objectHandle: 1001,
    name: 'DSC_1042.JPG',
    takenAt: '2026-05-18T09:24:00+08:00',
    sizeBytes: 14_280_000,
    width: 5568,
    height: 3712,
    format: 'JPG',
    thumbnailUrl:
      'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=640&q=80',
    isRaw: false
  },
  {
    id: 'demo-2',
    objectHandle: 1002,
    name: 'DSC_1043.NEF',
    takenAt: '2026-05-18T09:31:00+08:00',
    sizeBytes: 32_870_000,
    width: 5568,
    height: 3712,
    format: 'NEF',
    thumbnailUrl:
      'https://images.unsplash.com/photo-1519681393784-d120267933ba?auto=format&fit=crop&w=640&q=80',
    isRaw: true
  },
  {
    id: 'demo-3',
    objectHandle: 1003,
    name: 'DSC_1044.JPG',
    takenAt: '2026-05-18T10:08:00+08:00',
    sizeBytes: 11_940_000,
    width: 5568,
    height: 3712,
    format: 'JPG',
    thumbnailUrl:
      'https://images.unsplash.com/photo-1516035069371-29a1b244cc32?auto=format&fit=crop&w=640&q=80',
    isRaw: false
  },
  {
    id: 'demo-4',
    objectHandle: 1004,
    name: 'DSC_1045.JPG',
    takenAt: '2026-05-18T10:16:00+08:00',
    sizeBytes: 15_510_000,
    width: 5568,
    height: 3712,
    format: 'JPG',
    thumbnailUrl:
      'https://images.unsplash.com/photo-1484704849700-f032a568e944?auto=format&fit=crop&w=640&q=80',
    isRaw: false
  },
  {
    id: 'demo-5',
    objectHandle: 1005,
    name: 'DSC_1046.JPG',
    takenAt: '2026-05-18T10:42:00+08:00',
    sizeBytes: 13_020_000,
    width: 5568,
    height: 3712,
    format: 'JPG',
    thumbnailUrl:
      'https://images.unsplash.com/photo-1498036882173-b41c28a8ba34?auto=format&fit=crop&w=640&q=80',
    isRaw: false
  },
  {
    id: 'demo-6',
    objectHandle: 1006,
    name: 'DSC_1047.NEF',
    takenAt: '2026-05-18T11:05:00+08:00',
    sizeBytes: 34_190_000,
    width: 5568,
    height: 3712,
    format: 'NEF',
    thumbnailUrl:
      'https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?auto=format&fit=crop&w=640&q=80',
    isRaw: true
  }
];

export class NikonCameraClient {
  private demoMode = !Capacitor.isNativePlatform();

  async connect(model: CameraModel, mode: ConnectionMode, host: string, wifiPassword: string) {
    if (this.demoMode) {
      await wait(700);
      return {
        connected: true,
        model,
        mode,
        host,
        cameraName: model === 'Z30' ? 'Nikon Z 30' : 'Nikon Z 5II',
        sessionId: Date.now()
      };
    }

    return NativeNikonCamera.connect({ model, mode, host, wifiPassword });
  }

  async listPhotos() {
    if (this.demoMode) {
      await wait(500);
      return demoPhotos;
    }

    const { photos } = await NativeNikonCamera.listPhotos();
    return photos;
  }

  async requestStoragePermission() {
    if (this.demoMode) {
      await wait(150);
      return 'granted';
    }

    const result = await NativeNikonCamera.requestStoragePermission();
    return result.storage;
  }

  async downloadPhotos(
    objectHandles: number[],
    size: DownloadSize,
    albumName: string,
    onProgress: (done: number, total: number) => void
  ) {
    if (this.demoMode) {
      for (let index = 0; index < objectHandles.length; index += 1) {
        await wait(450);
        onProgress(index + 1, objectHandles.length);
      }
      return objectHandles.length;
    }

    const { saved } = await NativeNikonCamera.downloadPhotos({ objectHandles, size, albumName });
    onProgress(saved, objectHandles.length);
    return saved;
  }

  async disconnect() {
    if (this.demoMode) {
      await wait(200);
      return;
    }

    await NativeNikonCamera.disconnect();
  }
}

function wait(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}
