export type CameraModel = 'Z30' | 'Z5II';

export type ConnectionMode = 'ap' | 'sta';

export type DownloadSize = '2mp' | '8mp' | 'original';

export interface CameraProfile {
  id: CameraModel;
  name: string;
  supportedModes: ConnectionMode[];
  defaultHost: string;
  imageUrl: string;
  notes: string;
}

export interface CameraConnection {
  connected: boolean;
  model: CameraModel;
  mode: ConnectionMode;
  host: string;
  cameraName?: string;
  serialNumber?: string;
  sessionId?: number;
}

export interface CameraPhoto {
  id: string;
  objectHandle: number;
  name: string;
  takenAt: string;
  sizeBytes: number;
  width: number;
  height: number;
  format: 'JPG' | 'NEF' | 'MOV' | 'OTHER';
  thumbnailUrl: string;
  isRaw: boolean;
}

export interface DownloadJob {
  id: string;
  name: string;
  progress: number;
  status: 'queued' | 'running' | 'done' | 'failed';
  message?: string;
}

export interface DownloadOptions {
  objectHandles: number[];
  size: DownloadSize;
  albumName: string;
}
