import {
  Camera,
  Check,
  ChevronDown,
  Download,
  Image,
  Images,
  Loader2,
  RadioTower,
  RefreshCw,
  Smartphone,
  Wifi,
  WifiOff
} from 'lucide-react';
import { useMemo, useState } from 'react';
import { cameraProfiles, downloadSizeLabel } from './cameraProfiles';
import { NikonCameraClient } from './nikonCamera';
import type { CameraConnection, CameraModel, CameraPhoto, ConnectionMode, DownloadJob, DownloadSize } from './types';

const cameraClient = new NikonCameraClient();

export function App() {
  const [model, setModel] = useState<CameraModel>('Z30');
  const [mode, setMode] = useState<ConnectionMode>('ap');
  const [host, setHost] = useState('192.168.1.1');
  const [size, setSize] = useState<DownloadSize>('8mp');
  const [connection, setConnection] = useState<CameraConnection | null>(null);
  const [photos, setPhotos] = useState<CameraPhoto[]>([]);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [busy, setBusy] = useState<'connect' | 'refresh' | 'download' | null>(null);
  const [message, setMessage] = useState('请选择相机并连接 Wi-Fi。');
  const [jobs, setJobs] = useState<DownloadJob[]>([]);

  const profile = cameraProfiles.find((item) => item.id === model) ?? cameraProfiles[0];
  const selectedPhotos = useMemo(() => photos.filter((photo) => selected.has(photo.objectHandle)), [photos, selected]);
  const canUseSta = profile.supportedModes.includes('sta');

  async function connect() {
    setBusy('connect');
    setMessage('正在连接相机 PTP/IP 服务...');
    try {
      const nextConnection = await cameraClient.connect(model, mode, host.trim());
      setConnection(nextConnection);
      setMessage(`${nextConnection.cameraName ?? profile.name} 已连接，正在读取照片。`);
      await refreshPhotos();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '连接失败，请确认手机已连接相机 Wi-Fi。');
    } finally {
      setBusy(null);
    }
  }

  async function refreshPhotos() {
    setBusy((current) => current ?? 'refresh');
    try {
      const nextPhotos = await cameraClient.listPhotos();
      setPhotos(nextPhotos);
      setSelected(new Set());
      setMessage(`已读取 ${nextPhotos.length} 张照片。`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '读取照片失败。');
    } finally {
      setBusy(null);
    }
  }

  async function disconnect() {
    await cameraClient.disconnect();
    setConnection(null);
    setPhotos([]);
    setSelected(new Set());
    setJobs([]);
    setMessage('已断开连接。');
  }

  async function downloadSelected() {
    if (selectedPhotos.length === 0) {
      setMessage('请先选择要下载的照片。');
      return;
    }

    const objectHandles = selectedPhotos.map((photo) => photo.objectHandle);
    setBusy('download');
    setJobs(
      selectedPhotos.map((photo) => ({
        id: photo.id,
        name: photo.name,
        progress: 0,
        status: 'queued'
      }))
    );
    setMessage(`正在下载 ${selectedPhotos.length} 张照片，尺寸：${downloadSizeLabel[size]}。`);

    try {
      const saved = await cameraClient.downloadPhotos(objectHandles, size, (done, total) => {
        setJobs((current) =>
          current.map((job, index) => ({
            ...job,
            status: index < done ? 'done' : index === done ? 'running' : 'queued',
            progress: index < done ? 100 : index === done ? 35 : 0
          }))
        );
        setMessage(`下载进度 ${done}/${total}`);
      });
      setJobs((current) => current.map((job) => ({ ...job, status: 'done', progress: 100 })));
      setMessage(`已保存 ${saved} 张照片到手机相册。`);
    } catch (error) {
      setJobs((current) =>
        current.map((job) => (job.status === 'done' ? job : { ...job, status: 'failed', message: '下载失败' }))
      );
      setMessage(error instanceof Error ? error.message : '下载失败。');
    } finally {
      setBusy(null);
    }
  }

  function updateModel(nextModel: CameraModel) {
    const nextProfile = cameraProfiles.find((item) => item.id === nextModel) ?? cameraProfiles[0];
    setModel(nextModel);
    setMode(nextProfile.supportedModes[0]);
    setHost(nextProfile.defaultHost);
  }

  function toggleSelection(handle: number) {
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(handle)) {
        next.delete(handle);
      } else {
        next.add(handle);
      }
      return next;
    });
  }

  function selectAll() {
    setSelected(new Set(photos.map((photo) => photo.objectHandle)));
  }

  function clearSelection() {
    setSelected(new Set());
  }

  return (
    <main className="app-shell">
      <section className="control-rail">
        <div className="brand-lockup">
          <div className="brand-mark">
            <Camera size={28} />
          </div>
          <div>
            <p className="eyebrow">Nikon Transfer</p>
            <h1>尼康图传</h1>
          </div>
        </div>

        <div className="status-panel">
          <div className={`signal ${connection ? 'online' : ''}`}>
            {connection ? <Wifi size={22} /> : <WifiOff size={22} />}
          </div>
          <div>
            <strong>{connection ? '相机已连接' : '等待连接'}</strong>
            <span>{message}</span>
          </div>
        </div>

        <label className="field">
          <span>相机型号</span>
          <div className="select-wrap">
            <select value={model} onChange={(event) => updateModel(event.target.value as CameraModel)}>
              {cameraProfiles.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name}
                </option>
              ))}
            </select>
            <ChevronDown size={18} />
          </div>
        </label>

        <div className="segmented" aria-label="连接模式">
          <button className={mode === 'ap' ? 'active' : ''} onClick={() => setMode('ap')}>
            <Wifi size={17} />
            AP
          </button>
          <button className={mode === 'sta' ? 'active' : ''} disabled={!canUseSta} onClick={() => setMode('sta')}>
            <RadioTower size={17} />
            STA
          </button>
        </div>

        <label className="field">
          <span>{mode === 'ap' ? '相机热点地址' : '相机局域网地址'}</span>
          <input value={host} onChange={(event) => setHost(event.target.value)} placeholder="192.168.1.1" />
        </label>

        <p className="profile-note">{profile.notes}</p>

        <div className="button-row">
          {connection ? (
            <button className="secondary-action" onClick={disconnect}>
              <WifiOff size={18} />
              断开
            </button>
          ) : (
            <button className="primary-action" disabled={busy === 'connect'} onClick={connect}>
              {busy === 'connect' ? <Loader2 className="spin" size={18} /> : <Smartphone size={18} />}
              连接相机
            </button>
          )}
          <button className="icon-action" disabled={!connection || busy === 'refresh'} onClick={refreshPhotos} title="刷新">
            {busy === 'refresh' ? <Loader2 className="spin" size={18} /> : <RefreshCw size={18} />}
          </button>
        </div>
      </section>

      <section className="workspace">
        <header className="toolbar">
          <div>
            <p className="eyebrow">Camera Roll</p>
            <h2>相机照片预览</h2>
          </div>
          <div className="toolbar-actions">
            <button onClick={selectAll} disabled={photos.length === 0}>
              <Images size={17} />
              全选
            </button>
            <button onClick={clearSelection} disabled={selected.size === 0}>
              清空
            </button>
          </div>
        </header>

        <div className="download-strip">
          <div className="size-picker">
            {(Object.keys(downloadSizeLabel) as DownloadSize[]).map((item) => (
              <button key={item} className={size === item ? 'active' : ''} onClick={() => setSize(item)}>
                {downloadSizeLabel[item]}
              </button>
            ))}
          </div>
          <button className="download-action" disabled={selected.size === 0 || busy === 'download'} onClick={downloadSelected}>
            {busy === 'download' ? <Loader2 className="spin" size={18} /> : <Download size={18} />}
            下载 {selected.size > 0 ? selected.size : ''} 张
          </button>
        </div>

        {photos.length === 0 ? (
          <div className="empty-state">
            <Image size={44} />
            <h3>连接相机后会显示照片缩略图</h3>
            <p>Z30 使用 AP 模式，Z5II 可按拍摄环境选择 AP 或 STA 模式。</p>
          </div>
        ) : (
          <div className="photo-grid">
            {photos.map((photo) => (
              <button
                key={photo.id}
                className={`photo-card ${selected.has(photo.objectHandle) ? 'selected' : ''}`}
                onClick={() => toggleSelection(photo.objectHandle)}
              >
                <img src={photo.thumbnailUrl} alt={photo.name} />
                <span className="check-dot">{selected.has(photo.objectHandle) ? <Check size={16} /> : null}</span>
                <span className="format-badge">{photo.format}</span>
                <strong>{photo.name}</strong>
                <small>
                  {formatMegabytes(photo.sizeBytes)} · {photo.width}×{photo.height}
                </small>
              </button>
            ))}
          </div>
        )}

        {jobs.length > 0 ? (
          <aside className="download-queue">
            {jobs.map((job) => (
              <div key={job.id} className="queue-row">
                <span>{job.name}</span>
                <div className="progress-track">
                  <div style={{ width: `${job.progress}%` }} />
                </div>
                <b>{job.status === 'done' ? '完成' : job.status === 'failed' ? '失败' : '等待'}</b>
              </div>
            ))}
          </aside>
        ) : null}
      </section>
    </main>
  );
}

function formatMegabytes(bytes: number) {
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}
