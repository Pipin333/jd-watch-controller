# 🕺 JD Watch Controller (Wear OS + Android) — v0.1.2

[English](README.md) • [Español](README_ES.md) • **[Português](README_PT.md)**

> **Controle de pontuação de movimento de código aberto para Just Dance em smartwatches Wear OS (Samsung Galaxy Watch 4, 5, 6, 7 / Pixel Watch) e smartphones Android.**  
> Compatível com Just Dance 2017 PC, Just Dance 2018–2022 e servidores/mods comunitários (*Legacy PC / JDLO*).

---

## 🌟 Novidades na v0.1.2

* 🔋 **Modo Standby e Economia de Bateria no Relógio:** Suspensão automática sincronizada com o estado do jogo (`isScoringActive`). Durante os menus e seleção de músicas, o relógio desliga os sensores e LEDs verdes PPG ópticos, libera o WakeLock do processador, muda a tela para preto absoluto AMOLED e agrupa os envios Bluetooth a cada ~40 ms (permitindo modo sniff de baixo consumo), reduzindo o consumo do relógio em até 80%!
* 🔄 **Navegação de Menus com o Bisel Giratório:** Navegue pelas músicas e menus do Just Dance girando o bisel físico ou virtual do relógio (`DIREITA` / `ESQUERDA`) com retorno tátil de micro-click instantâneo, e toque na tela para `CONFIRMAR` a seleção.
* 🧭 **Fusão de Sensores de 6 Eixos (Acelerômetro + Giroscópio):** Leitura de velocidade angular 3D a 50 Hz (`TYPE_GYROSCOPE`, pacote tipo `0x03`) junto com aceleração linear, com espelhamento de eixos para pulso esquerdo.
* ⏱️ **Auto-Recap ao Finalizar a Música:** Temporizador automático de 8 segundos pós-dança que finaliza a sessão Sweat e exibe o resumo completo com linha do tempo de passos, mesmo se mods comunitários ou Legacy PC não enviarem o pacote de `"recap"`.
* 🎯 **Curva de Pontuação Ponderada Oficial:** Calibração exata da precisão Just Dance ($1.00 \times \text{Perfect} + 0.82 \times \text{Super} + 0.60 \times \text{Good} + 0.35 \times \text{OK}$) para pontuações realistas de ~11.5k+, com limites dinâmicos de Força-G no pulso (`PERFECT >= 1.70G`, `SUPER >= 1.20G`).
* 🛡️ **Filtro de Mensagens de Console e Correção de Gold Moves:** Mapeamento estrito de sons (`else -> null`) eliminando falsos Gold Moves, e lista de bloqueio de textos que impede que *"Use o controle do console para continuar"* sobrescreva o título da música.

---

## 🏗️ Arquitetura do Sistema

```
[ Galaxy Watch 4 / Wear OS ] 
       │
       │ BLE / Wi-Fi (Google Play Services ChannelClient)
       │ Fluxo contínuo binário de 21 bytes Little-Endian (50 Hz exatos)
       ▼
[ Smartphone Android (Companion App) ]
       │
       │ Wi-Fi Local (WebSocket ws://<pc_ip>:8080/smartphone)
       │ Handshake oficial + pacotes JD_PhoneScoringData (~2 ms de latência)
       ▼
[ PC / Console (Just Dance 2017 / Legacy PC) ]
```

---

## 🚀 Requisitos

* **PC / Console:** Just Dance 2017 para PC ou servidor compatível na mesma rede Wi-Fi local.
* **Smartphone:** Android 8.0+ (Oreo ou superior).
* **Smartwatch:** Wear OS 3.0+ (Samsung Galaxy Watch 4, 5, 6, 7, Pixel Watch ou similar), preferencialmente no pulso direito.

---

## 🛠️ Compilação a partir do Código Fonte

O projeto utiliza Gradle e Kotlin 2.2 com Jetpack Compose no celular e Wear Compose no relógio:

```powershell
# Clonar o repositório
git clone https://github.com/Pipin333/jd-watch-controller.git
cd jd-watch-controller

# Compilar ambos os APKs (celular e relógio)
.\gradlew.bat assembleDebug
```

Os APKs gerados estarão localizados em:
* Relógio: `app/build/outputs/apk/debug/app-debug.apk`
* Celular: `myapplication/build/outputs/apk/debug/myapplication-debug.apk`

---

## 📲 Instalação Rápida via ADB Sem Fio

### 1. Ativar opções de desenvolvedor nos dois aparelhos
* No celular: *Configurações > Opções do desenvolvedor > Depuração sem fio*.
* No relógio: *Configurações > Opções do desenvolvedor > Depuração ADB + Depuração sem fio*.

### 2. Instalar via linha de comando:
```powershell
# Conectar via ADB Wi-Fi
adb connect <IP_CELULAR>:<PORTA>
adb connect <IP_RELOGIO>:<PORTA>

# Instalar app do celular
adb -s <IP_CELULAR>:<PORTA> install -r myapplication/build/outputs/apk/debug/myapplication-debug.apk

# Instalar app do relógio
adb -s <IP_RELOGIO>:<PORTA> install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🎮 Como Jogar

1. **Abra o jogo no PC** (*Just Dance 2017* ou *Legacy PC*) na tela de seleção de músicas.
2. **Abra o `JD Watch Controller` no celular:**
   * O aplicativo iniciará a busca automática na rede Wi-Fi local.
   * Quando o PC for encontrado, toque em **"CONECTAR"** (ou insira o IP manualmente caso a rede bloqueie UDP broadcast).
3. **Selecione o seu Dançarino:** Em músicas com múltiplos dançarinos, selecione `[ 🕺 Coach 1..4 ]` e confirme com `[ ✔ Confirmar ]`.
4. **Abra o `JD Watch Controller` no relógio:**
   * Toque em **"INICIAR STREAMING"**.
   * Você verá no celular a frequência em tempo real (50 Hz) e os batimentos cardíacos (BPM).
5. **Dance!** Escolha a música no PC. O celular alternará automaticamente para o painel de telemetria em tempo real, calculando calorias gastas ($Kcal$), vigor e vibrando a cada estrela e Gold Move.

---

## 🐍 Ferramentas de PC (`tools/`)

* **`tools/jd_haptic_bridge.py`:** Utilitário interativo em Python para testar e disparar vibrações hápticas no relógio com latência zero (<2 ms) via UDP (`:42069`):
  ```powershell
  python tools/jd_haptic_bridge.py --ip <IP_CELULAR>
  ```
* **`tools/inspect_jd.py`:** Inspetor de pacotes WebSocket e sniffer de rede do Just Dance.

---

## 📄 Licença

Projeto de código aberto sob a [Licença MIT](LICENSE). Desenvolvido para fins educacionais e de preservação da comunidade de Just Dance.
