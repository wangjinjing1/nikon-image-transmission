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
  Settings,
  Smartphone,
  Wifi,
  WifiOff
} from 'lucide-react';
import { useMemo, useState } from 'react';
import { cameraProfiles, downloadSizeLabel } from './cameraProfiles';
import { NikonCameraClient } from './nikonCamera';
import type { CameraConnection, CameraModel, CameraPhoto, ConnectionMode, DownloadJob, DownloadSize } from './types';

const cameraClient = new NikonCameraClient();

type ActiveView = 'connect' | 'download';

export function App() {
  const [activeView, setActiveView] = useState<ActiveView>('connect');
  const [model, setModel] = useState<CameraModel>('Z30');
  const [mode, setMode] = useState<ConnectionMode>('ap');
  const [host, setHost] = useState('');
  const [size, setSize] = useState<DownloadSize>('8mp');
  const [connection, setConnection] = useState<CameraConnection | null>(null);
  const [photos, setPhotos] = useState<CameraPhoto[]>([]);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [busy, setBusy] = useState<'connect' | 'refresh' | 'download' | null>(null);
  const [message, setMessage] = useState('请选择相机并连接 Wi-Fi。');
  const [jobs, setJobs] = useState<DownloadJob[]>([]);
  const [showSettings, setShowSettings] = useState(false);
  const [albumName, setAlbumName] = useState(() => window.localStorage.getItem('downloadAlbumName') ?? '尼康图传');

  const profile = cameraProfiles.find((item) => item.id === model) ?? cameraProfiles[0];
  const selectedPhotos = useMemo(() => photos.filter((photo) => selected.has(photo.objectHandle)), [photos, selected]);
  const canUseSta = profile.supportedModes.includes('sta');
  const connectionHost = mode === 'ap' ? '' : host.trim();

  async function connect() {
    setBusy('connect');
    setMessage(
      mode === 'ap'
        ? '正在连接相机热点服务...'
        : connectionHost
          ? '正在连接局域网相机服务...'
          : '正在自动发现 STA 模式相机...'
    );
    try {
      const nextConnection = await cameraClient.connect(model, mode, connectionHost);
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
    const targetAlbum = sanitizeAlbumName(albumName);
    setAlbumName(targetAlbum);
    window.localStorage.setItem('downloadAlbumName', targetAlbum);

    const permission = await cameraClient.requestStoragePermission();
    if (permission !== 'granted') {
      setMessage('没有获得文件/照片保存权限，请允许后再下载。');
      return;
    }

    setBusy('download');
    setJobs(
      selectedPhotos.map((photo) => ({
        id: photo.id,
        name: photo.name,
        progress: 0,
        status: 'queued'
      }))
    );
    setMessage(`正在后台下载 ${selectedPhotos.length} 张照片，尺寸：${downloadSizeLabel[size]}。可继续浏览照片或切到其他应用。`);

    try {
      const saved = await cameraClient.downloadPhotos(objectHandles, size, targetAlbum, (done, total) => {
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
      setMessage(`已保存 ${saved} 张照片到 Pictures/${targetAlbum}。`);
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

  function updateMode(nextMode: ConnectionMode) {
    setMode(nextMode);
    if (nextMode === 'ap') {
      setHost('');
    }
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

  function openDownloadView() {
    if (!connection) {
      setMessage('请先连接相机，再进入下载照片页面。');
      setActiveView('connect');
      return;
    }

    setActiveView('download');
  }

  return (
    <main className="app-shell">
      <header className="app-topbar">
        <div className="brand-lockup compact">
          <div className="brand-mark">
            <Camera size={28} />
          </div>
          <div>
            <p className="eyebrow">Nikon Transfer</p>
            <h1>尼康图传</h1>
          </div>
        </div>

        <nav className="page-tabs" aria-label="页面切换">
          <button className={activeView === 'connect' ? 'active' : ''} onClick={() => setActiveView('connect')}>
            <Wifi size={18} />
            连接相机
          </button>
          <button className={activeView === 'download' ? 'active' : ''} onClick={openDownloadView}>
            <Images size={18} />
            下载照片
          </button>
        </nav>
      </header>

      <section className={`page-panel connect-panel ${activeView === 'connect' ? 'active' : ''}`} hidden={activeView !== 'connect'}>
        <div className="control-rail">
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
          <button className={mode === 'ap' ? 'active' : ''} onClick={() => updateMode('ap')}>
            <Wifi size={17} />
            AP
          </button>
          <button className={mode === 'sta' ? 'active' : ''} disabled={!canUseSta} onClick={() => updateMode('sta')}>
            <RadioTower size={17} />
            STA
          </button>
        </div>

        {mode === 'ap' ? (
          <div className="ap-hint">
            <strong>AP 模式会自动检测相机地址</strong>
            <span>请先在手机系统 Wi-Fi 中连接相机热点，APP 会读取当前热点网关并连接相机。</span>
          </div>
        ) : (
          <div className="sta-connect-block">
            <div className="ap-hint">
              <strong>STA 模式会自动发现相机</strong>
              <span>请先让相机连接本手机热点或同一 Wi-Fi，APP 会扫描当前网络中的尼康 PTP/IP 服务；发现失败时可手动填写 IP。</span>
            </div>
            <label className="field">
              <span>手动 IP 地址（可选）</span>
              <input value={host} onChange={(event) => setHost(event.target.value)} placeholder="发现失败时填写，例如 192.168.43.23" />
            </label>
          </div>
        )}

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
          <button className="icon-action" onClick={() => setShowSettings((current) => !current)} title="设置">
            <Settings size={18} />
          </button>
        </div>

        <button className="download-page-action" onClick={openDownloadView}>
          <Images size={18} />
          {connection ? '下载照片' : '请先连接相机'}
        </button>

        {showSettings ? (
          <div className="settings-panel">
            <label className="field">
              <span>下载目录</span>
              <input
                value={albumName}
                onChange={(event) => setAlbumName(event.target.value)}
                onBlur={() => {
                  const nextAlbumName = sanitizeAlbumName(albumName);
                  setAlbumName(nextAlbumName);
                  window.localStorage.setItem('downloadAlbumName', nextAlbumName);
                }}
                placeholder="尼康图传"
              />
            </label>
            <p>默认保存到手机 Pictures 目录下的这个文件夹。</p>
          </div>
        ) : null}
        </div>
      </section>

      <section className={`page-panel download-panel ${activeView === 'download' ? 'active' : ''}`} hidden={activeView !== 'download'}>
        <div className="workspace">
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
          <div className="download-meta">
            <strong>{selected.size} 张已选择</strong>
            <span>保存到 Pictures/{sanitizeAlbumName(albumName)}</span>
          </div>
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
            {connection ? <Image size={44} /> : <WifiOff size={44} />}
            <h3>{connection ? '相机里暂时没有可下载照片' : '请先连接相机'}</h3>
            <p>{connection ? '点击刷新按钮重新读取相机照片。' : '回到连接相机页面，连接相机热点或使用 STA 自动发现后再下载。'}</p>
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
        </div>
      </section>
    </main>
  );
}

function formatMegabytes(bytes: number) {
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function sanitizeAlbumName(value: string) {
  const cleaned = value.replace(/[\\/:*?"<>|]/g, '').trim();
  return cleaned.length > 0 ? cleaned : '尼康图传';
}
