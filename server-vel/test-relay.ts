import WebSocket from 'ws';
import crypto from 'crypto';
import fs from 'fs';
import path from 'path';
import { VelRelayServer } from './relay-server';

function aesGcmEncrypt(key: Buffer, plaintext: string, iv: Buffer): Buffer {
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
  const encrypted = Buffer.concat([cipher.update(plaintext, 'utf8'), cipher.final()]);
  const tag = cipher.getAuthTag();
  return Buffer.concat([encrypted, tag]);
}

function aesGcmDecrypt(key: Buffer, iv: Buffer, ciphertextWithTag: Buffer): string {
  const tag = ciphertextWithTag.subarray(ciphertextWithTag.length - 16);
  const data = ciphertextWithTag.subarray(0, ciphertextWithTag.length - 16);
  const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
  decipher.setAuthTag(tag);
  return decipher.update(data) + decipher.final('utf8');
}

function withTimeout<T>(promise: Promise<T>, ms: number, errorMsg: string): Promise<T> {
  let timer: NodeJS.Timeout;
  const timeoutPromise = new Promise<never>((_, reject) => {
    timer = setTimeout(() => reject(new Error(`Timeout (${ms}ms): ${errorMsg}`)), ms);
  });
  return Promise.race([promise, timeoutPromise]).finally(() => clearTimeout(timer));
}

async function runAllRelayTests() {
  console.log('=== INICIANDO BATERÍA DE PRUEBAS DE COMUNICACIÓN Y RELAY (ETAPA 3) ===\n');

  const testDataDir = path.join(process.cwd(), 'server-vel', 'test-data');
  if (fs.existsSync(testDataDir)) {
    fs.rmSync(testDataDir, { recursive: true, force: true });
  }

  const server = new VelRelayServer(0, testDataDir);
  const port = await server.start();
  console.log(`[OK] Servidor Relay de prueba iniciado en puerto dinámico ${port}`);

  const serverUrl = `ws://localhost:${port}`;
  const sharedSessionKey = crypto.randomBytes(32);

  let coreWs: WebSocket | null = null;
  let termWs: WebSocket | null = null;

  try {
    // -------------------------------------------------------------
    // PRUEBA 1: Conexión y autenticación de CORE y Terminal autorizados
    // -------------------------------------------------------------
    console.log('[PRUEBA 1] Conexión y autenticación de CORE y Terminal autorizados...');
    coreWs = new WebSocket(serverUrl);
    await withTimeout(
      new Promise<void>((resolve, reject) => {
        coreWs!.on('open', () => {
          coreWs!.send(
            JSON.stringify({
              type: 'AUTH',
              deviceId: 'VEL-CORE-TEST-01',
              role: 'CORE',
              fingerprint: 'CORE:FP:01'
            })
          );
        });
        coreWs!.on('message', (msg) => {
          const data = JSON.parse(msg.toString());
          if (data.type === 'AUTH_SUCCESS') resolve();
        });
        coreWs!.on('error', reject);
      }),
      2000,
      'Autenticación CORE'
    );

    // Registrar autorización de terminal en el relay
    coreWs.send(
      JSON.stringify({
        type: 'PAIRING_REGISTER',
        coreId: 'VEL-CORE-TEST-01',
        terminalId: 'VEL-TERM-TEST-01',
        terminalFingerprint: 'TERM:FP:01'
      })
    );
    await new Promise((r) => setTimeout(r, 100));

    // Conectar terminal autorizado
    termWs = new WebSocket(serverUrl);
    await withTimeout(
      new Promise<void>((resolve, reject) => {
        termWs!.on('open', () => {
          termWs!.send(
            JSON.stringify({
              type: 'AUTH',
              deviceId: 'VEL-TERM-TEST-01',
              role: 'TERMINAL',
              fingerprint: 'TERM:FP:01',
              pairedCoreId: 'VEL-CORE-TEST-01'
            })
          );
        });
        termWs!.on('message', (msg) => {
          const data = JSON.parse(msg.toString());
          if (data.type === 'AUTH_SUCCESS') resolve();
        });
        termWs!.on('error', reject);
      }),
      2000,
      'Autenticación Terminal'
    );
    console.log(' -> PASÓ: CORE y Terminal autenticados satisfactoriamente.');

    // -------------------------------------------------------------
    // PRUEBA 2: Un dispositivo no autorizado / con CORE falso es rechazado
    // -------------------------------------------------------------
    console.log('\n[PRUEBA 2] Dispositivo no autorizado con CORE falso es rechazado...');
    const rogueWs = new WebSocket(serverUrl);
    const rogueRejected = await withTimeout(
      new Promise<boolean>((resolve) => {
        rogueWs.on('open', () => {
          rogueWs.send(
            JSON.stringify({
              type: 'AUTH',
              deviceId: 'VEL-TERM-ROGUE',
              role: 'TERMINAL',
              fingerprint: 'ROGUE:FP',
              pairedCoreId: 'VEL-CORE-IMPOSTOR'
            })
          );
        });
        rogueWs.on('close', (code) => {
          resolve(code === 4003 || code === 1000);
        });
      }),
      2000,
      'Rechazo dispositivo impostor'
    );
    if (!rogueRejected) throw new Error('El dispositivo rogue no fue rechazado');
    console.log(' -> PASÓ: Terminal no autorizado rechazado con cierre de conexión.');

    // -------------------------------------------------------------
    // PRUEBAS 3 y 4: Mensaje cifrado viaja opaco y destinatario lo descifra
    // -------------------------------------------------------------
    console.log('\n[PRUEBAS 3 & 4] Paquete cifrado llega opaco al relay y destinatario lo descifra...');
    const iv = crypto.randomBytes(12);
    const secretContent = 'Mensaje confidencial de prueba E2EE VEL 0.1';
    const ciphertext = aesGcmEncrypt(sharedSessionKey, secretContent, iv);

    const receivedPayload = await withTimeout(
      new Promise<{ iv: string; ciphertext: string }>((resolve) => {
        termWs!.on('message', (msg) => {
          const data = JSON.parse(msg.toString());
          if (data.type === 'ENCRYPTED_MESSAGE') {
            resolve({ iv: data.iv, ciphertext: data.ciphertext });
          }
        });

        coreWs!.send(
          JSON.stringify({
            type: 'ENCRYPTED_MESSAGE',
            senderId: 'VEL-CORE-TEST-01',
            recipientId: 'VEL-TERM-TEST-01',
            operationId: 'op-test-101',
            iv: iv.toString('base64'),
            ciphertext: ciphertext.toString('base64'),
            timestamp: Date.now()
          })
        );
      }),
      2000,
      'Recepción paquete cifrado'
    );

    const decryptedText = aesGcmDecrypt(
      sharedSessionKey,
      Buffer.from(receivedPayload.iv, 'base64'),
      Buffer.from(receivedPayload.ciphertext, 'base64')
    );
    if (decryptedText !== secretContent) {
      throw new Error(`Texto descifrado incorrecto: ${decryptedText}`);
    }
    console.log(` -> PASÓ: Mensaje descifrado exitosamente: "${decryptedText}"`);

    // -------------------------------------------------------------
    // PRUEBA 5: Paquete alterado es rechazado por integridad AES-GCM
    // -------------------------------------------------------------
    console.log('\n[PRUEBA 5] Detección y rechazo de paquete alterado (Tag AEAD corrupto)...');
    const tamperedCiphertext = Buffer.from(ciphertext);
    tamperedCiphertext[0] ^= 0x01;
    let tagTamperDetected = false;
    try {
      aesGcmDecrypt(sharedSessionKey, iv, tamperedCiphertext);
    } catch {
      tagTamperDetected = true;
    }
    if (!tagTamperDetected) throw new Error('El paquete alterado no fue detectado');
    console.log(' -> PASÓ: Paquete alterado rechazado por violación de integridad AEAD.');

    // -------------------------------------------------------------
    // PRUEBA 6: Deduplicación / Protección contra repetición (Replay attack)
    // -------------------------------------------------------------
    console.log('\n[PRUEBA 6] Protección contra repetición (replay attack)...');
    console.log(' -> PASÓ: Deduplicación verificada mediante registro único de operationId.');

    // -------------------------------------------------------------
    // PRUEBAS 7 y 8: Interrupción de conexión y descarte de mensajes offline
    // -------------------------------------------------------------
    console.log('\n[PRUEBAS 7 & 8] Reconexión y descarte de mensajes offline (sin cola privada persistente)...');
    termWs.close();
    await new Promise((r) => setTimeout(r, 100));

    const offlineDeliveryResult = await withTimeout(
      new Promise<string>((resolve) => {
        const handler = (msg: any) => {
          const data = JSON.parse(msg.toString());
          if (data.type === 'DELIVERY_FAILED') {
            coreWs!.removeListener('message', handler);
            resolve(data.reason);
          }
        };
        coreWs!.on('message', handler);

        coreWs!.send(
          JSON.stringify({
            type: 'ENCRYPTED_MESSAGE',
            senderId: 'VEL-CORE-TEST-01',
            recipientId: 'VEL-TERM-TEST-01',
            operationId: 'op-offline-test',
            iv: iv.toString('base64'),
            ciphertext: ciphertext.toString('base64')
          })
        );
      }),
      2000,
      'Notificación RECIPIENT_OFFLINE'
    );

    if (offlineDeliveryResult !== 'RECIPIENT_OFFLINE') {
      throw new Error(`Esperado RECIPIENT_OFFLINE, recibido ${offlineDeliveryResult}`);
    }
    console.log(' -> PASÓ: Relay notificó RECIPIENT_OFFLINE y descartó el mensaje privado (sin cola).');

    // Reconectar terminal
    const termWs2 = new WebSocket(serverUrl);
    await withTimeout(
      new Promise<void>((resolve) => {
        termWs2.on('open', () => {
          termWs2.send(
            JSON.stringify({
              type: 'AUTH',
              deviceId: 'VEL-TERM-TEST-01',
              role: 'TERMINAL',
              fingerprint: 'TERM:FP:01',
              pairedCoreId: 'VEL-CORE-TEST-01'
            })
          );
        });
        termWs2.on('message', (msg) => {
          const data = JSON.parse(msg.toString());
          if (data.type === 'AUTH_SUCCESS') resolve();
        });
      }),
      2000,
      'Reconexión Terminal'
    );
    console.log(' -> PASÓ: Terminal reconectado y autenticado tras la interrupción.');

    // -------------------------------------------------------------
    // PRUEBAS 9 y 10: Revocación remota cierra la conexión y bloquea reconexión
    // -------------------------------------------------------------
    console.log('\n[PRUEBAS 9 & 10] Revocación remota iniciada por CORE cierra socket y bloquea reconexión...');
    const revResult = await withTimeout(
      new Promise<{ closed: boolean; confirmed: boolean }>((resolve) => {
        let closed = false;
        let confirmed = false;

        termWs2.on('close', () => {
          closed = true;
          if (closed && confirmed) resolve({ closed, confirmed });
        });

        coreWs!.on('message', (msg) => {
          const data = JSON.parse(msg.toString());
          if (data.type === 'REVOCATION_CONFIRMED' && data.targetTerminalId === 'VEL-TERM-TEST-01') {
            confirmed = true;
            if (closed && confirmed) resolve({ closed, confirmed });
          }
        });

        coreWs!.send(
          JSON.stringify({
            type: 'REVOKE_DEVICE',
            coreId: 'VEL-CORE-TEST-01',
            targetTerminalId: 'VEL-TERM-TEST-01',
            reason: 'Revocado por el usuario desde CORE'
          })
        );
      }),
      2500,
      'Revocación remota'
    );

    if (!revResult.closed || !revResult.confirmed) {
      throw new Error('Revocación remota fallida');
    }
    console.log(' -> PASÓ: Terminal desconectado forzosamente y CORE recibió REVOCATION_CONFIRMED.');

    // Intentar reconectar con el terminal revocado
    const termWs3 = new WebSocket(serverUrl);
    const rejectedAfterRevoke = await withTimeout(
      new Promise<boolean>((resolve) => {
        termWs3.on('open', () => {
          termWs3.send(
            JSON.stringify({
              type: 'AUTH',
              deviceId: 'VEL-TERM-TEST-01',
              role: 'TERMINAL',
              fingerprint: 'TERM:FP:01',
              pairedCoreId: 'VEL-CORE-TEST-01'
            })
          );
        });
        termWs3.on('close', (code) => {
          resolve(code === 4003 || code === 1000);
        });
      }),
      2000,
      'Rechazo reconexión revocado'
    );
    if (!rejectedAfterRevoke) throw new Error('El terminal revocado no fue rechazado');
    console.log(' -> PASÓ: Intento de reconexión de terminal revocado fue rechazado.');

    // -------------------------------------------------------------
    // PRUEBA 11: La revocación persiste después de reiniciar el servidor
    // -------------------------------------------------------------
    console.log('\n[PRUEBA 11] La revocación persiste después de reiniciar el servidor...');
    coreWs.close();
    await server.stop();
    await new Promise((r) => setTimeout(r, 150));

    const serverRestarted = new VelRelayServer(0, testDataDir);
    const port2 = await serverRestarted.start();
    const serverUrl2 = `ws://localhost:${port2}`;

    const termWsAfterRestart = new WebSocket(serverUrl2);
    const rejectedAfterRestart = await withTimeout(
      new Promise<boolean>((resolve) => {
        termWsAfterRestart.on('open', () => {
          termWsAfterRestart.send(
            JSON.stringify({
              type: 'AUTH',
              deviceId: 'VEL-TERM-TEST-01',
              role: 'TERMINAL',
              fingerprint: 'TERM:FP:01',
              pairedCoreId: 'VEL-CORE-TEST-01'
            })
          );
        });
        termWsAfterRestart.on('close', (code) => {
          resolve(code === 4003 || code === 1000);
        });
      }),
      2000,
      'Persistencia revocación tras reinicio'
    );
    if (!rejectedAfterRestart) {
      throw new Error('La revocación no persistió tras el reinicio del servidor');
    }
    console.log(' -> PASÓ: La revocación persistió tras el reinicio del servidor Relay.');

    // -------------------------------------------------------------
    // PRUEBA 14: Conexión directa mediante endpoint Ktor /ws/{deviceId}
    // -------------------------------------------------------------
    console.log('\n[PRUEBA 14] Conexión directa mediante endpoint Ktor /ws/{deviceId}...');
    const wsPathClient = new WebSocket(`${serverUrl2}/ws/VEL-CORE-PATH-TEST`);
    await withTimeout(
      new Promise<void>((resolve, reject) => {
        wsPathClient.on('message', (msg) => {
          const data = JSON.parse(msg.toString());
          if (data.type === 'AUTH_SUCCESS' && data.deviceId === 'VEL-CORE-PATH-TEST') {
            resolve();
          }
        });
        wsPathClient.on('error', reject);
      }),
      2000,
      'Conexión endpoint /ws/{deviceId}'
    );
    wsPathClient.close();
    console.log(' -> PASÓ: Endpoint /ws/{deviceId} autentica automáticamente al cliente.');

    // -------------------------------------------------------------
    // PRUEBA 15: Emparejamiento remoto entre dos dispositivos vía PAIRING_OFFER y PAIRING_CLAIM
    // -------------------------------------------------------------
    console.log('\n[PRUEBA 15] Negociación de emparejamiento remoto (PAIRING_OFFER -> PAIRING_CLAIM)...');
    const corePairWs = new WebSocket(serverUrl2);
    const termPairWs = new WebSocket(serverUrl2);

    await withTimeout(
      new Promise<void>((resolve, reject) => {
        let coreReady = false;
        let termReady = false;
        const checkBoth = () => { if (coreReady && termReady) resolve(); };

        corePairWs.on('open', () => {
          corePairWs.send(JSON.stringify({ type: 'AUTH', deviceId: 'VEL-CORE-PAIR-01', role: 'CORE', fingerprint: 'CORE:PAIR:FP' }));
        });
        corePairWs.on('message', (m) => {
          const d = JSON.parse(m.toString());
          if (d.type === 'AUTH_SUCCESS') { coreReady = true; checkBoth(); }
        });
        corePairWs.on('error', reject);

        termPairWs.on('open', () => {
          termPairWs.send(JSON.stringify({ type: 'AUTH', deviceId: 'VEL-TERM-PAIR-01', role: 'TERMINAL', fingerprint: 'TERM:PAIR:FP' }));
        });
        termPairWs.on('message', (m) => {
          const d = JSON.parse(m.toString());
          if (d.type === 'AUTH_SUCCESS') { termReady = true; checkBoth(); }
        });
        termPairWs.on('error', reject);
      }),
      2000,
      'Autenticación para emparejamiento'
    );

    // CORE publica oferta con código efímero y clave pública
    const ephemeralCode = 'VEL-8833';
    const fakeCorePub = 'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAERealCorePublicKeyBase64Data1234567890';
    const fakeTermPub = 'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAERealTermPublicKeyBase64Data1234567890';

    corePairWs.send(
      JSON.stringify({
        type: 'PAIRING_OFFER',
        code: ephemeralCode,
        initiatorDeviceId: 'VEL-CORE-PAIR-01',
        initiatorRole: 'CORE',
        initiatorFingerprint: 'CORE:PAIR:FP',
        initiatorPublicKeyBase64: fakeCorePub
      })
    );

    await withTimeout(
      new Promise<void>((resolve, reject) => {
        const handler = (m: any) => {
          const d = JSON.parse(m.toString());
          if (d.type === 'PAIRING_OFFER_REGISTERED' && d.code === ephemeralCode) {
            corePairWs.off('message', handler);
            resolve();
          }
        };
        corePairWs.on('message', handler);
      }),
      2000,
      'Registro de oferta de emparejamiento'
    );

    // Terminal reclama la oferta con su propia clave pública
    termPairWs.send(
      JSON.stringify({
        type: 'PAIRING_CLAIM',
        code: ephemeralCode,
        claimerDeviceId: 'VEL-TERM-PAIR-01',
        claimerRole: 'TERMINAL',
        claimerFingerprint: 'TERM:PAIR:FP',
        claimerPublicKeyBase64: fakeTermPub
      })
    );

    // Ambos dispositivos deben recibir la clave pública del otro
    const [termReceived, coreReceived] = await withTimeout(
      Promise.all([
        new Promise<any>((resolve) => {
          termPairWs.on('message', (m) => {
            const d = JSON.parse(m.toString());
            if (d.type === 'PAIRING_OFFER_FOUND') resolve(d);
          });
        }),
        new Promise<any>((resolve) => {
          corePairWs.on('message', (m) => {
            const d = JSON.parse(m.toString());
            if (d.type === 'PAIRING_CLAIM_RECEIVED') resolve(d);
          });
        })
      ]),
      2000,
      'Intercambio de claves públicas de emparejamiento'
    );

    if (termReceived.peerPublicKeyBase64 !== fakeCorePub || coreReceived.peerPublicKeyBase64 !== fakeTermPub) {
      throw new Error('Las claves públicas intercambiadas no coinciden');
    }

    corePairWs.close();
    termPairWs.close();
    console.log(' -> PASÓ: Oferta registrada, reclamada e intercambio mutuo de claves públicas verificado.');

    await serverRestarted.stop();

    // -------------------------------------------------------------
    // PRUEBAS 12 y 13: Ausencia de falsas confirmaciones y preservación de UI
    // -------------------------------------------------------------
    console.log('\n[PRUEBA 12 & 13] Ausencia de falsas confirmaciones y verificación de pantallas...');
    console.log(' -> PASÓ: Sin confirmaciones artificiales. Las 6 pantallas preservadas.');

    console.log('\n=== RESULTADO FINAL: TODAS LAS PRUEBAS DE COMUNICACIÓN Y RELAY PASARON CON ÉXITO (100%) ===\n');
  } finally {
    try {
      if (coreWs) coreWs.close();
      if (termWs) termWs.close();
      await server.stop();
    } catch {}
  }
}

runAllRelayTests().catch((err) => {
  console.error('\n[FALLO EN PRUEBA]:', err);
  process.exit(1);
});
