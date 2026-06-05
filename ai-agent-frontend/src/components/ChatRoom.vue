<template>
  <div class="chat-container">
    <section class="chat-display-panel">
      <div class="chat-messages" ref="messagesContainer">
        <div v-for="(msg, index) in messages" :key="msg.id || index" class="message-row" :class="{ 'is-user': msg.isUser }">

          <!-- Agent reasoning trace — Neural Command Center -->
          <template v-if="msg.type === 'ai-trace'">
            <div class="avatar-col">
              <AiAvatarFallback :type="aiType" />
            </div>
            <div class="bubble-col trace-bubble-col">
              <div class="trace-card" :class="{ 'is-running': msg.trace?.status === 'running', 'is-done': msg.trace?.status === 'done', 'is-error': msg.trace?.status === 'error' }">
                <!-- Header with brain-wave equalizer -->
                <div class="trace-header" @click="emit('toggle-reasoning', msg.id)">
                  <div class="trace-header-left">
                    <div class="brain-wave" :class="{ idle: msg.trace?.status === 'done', error: msg.trace?.status === 'error' }">
                      <span v-for="n in 5" :key="n" class="wave-bar" :style="{ animationDelay: (n * 0.12) + 's', height: (10 + (n % 3) * 6) + 'px' }"></span>
                    </div>
                    <span class="trace-label">{{ traceHeaderText(msg.trace) }}</span>
                  </div>
                  <div class="trace-header-right">
                    <span class="trace-step-count" v-if="msg.trace?.steps?.length">{{ msg.trace.steps.length }} 步</span>
                    <span class="trace-duration" v-if="msg.trace?.status === 'done' && msg.trace?.duration">{{ msg.trace.duration }}</span>
                    <button v-if="msg.trace?.steps?.length" class="trace-toggle" type="button">
                      <svg viewBox="0 0 20 20" fill="currentColor" width="14" height="14" :class="{ rotated: !msg.trace?.collapsed }">
                        <path fill-rule="evenodd" d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z" clip-rule="evenodd"/>
                      </svg>
                    </button>
                  </div>
                </div>

                <!-- Body: command blocks -->
                <Transition name="trace-expand">
                  <div v-show="!msg.trace?.collapsed" class="trace-body">
                    <!-- Waiting: morphing dots -->
                    <div v-if="msg.trace?.status === 'running' && !(msg.trace?.steps?.length)" class="trace-waiting">
                      <div class="morphing-dots">
                        <span v-for="n in 3" :key="n" class="mdot" :style="{ animationDelay: (n * 0.18) + 's' }"></span>
                      </div>
                      <span class="waiting-text">{{ msg.trace?.progressText || '正在分析问题...' }}</span>
                    </div>

                    <!-- Command blocks list -->
                    <div class="command-list">
                      <TransitionGroup name="cmd-enter">
                        <div
                          v-for="(step, stepIndex) in (msg.trace?.steps || [])"
                          :key="`cmd-${step.stepNo}-${stepIndex}`"
                          class="cmd-block"
                          :class="[step.type, { 'is-latest': stepIndex === (msg.trace?.steps?.length || 0) - 1 && msg.trace?.status === 'running', 'is-typing': step.typing }]"
                        >
                          <!-- Block head: icon + badge -->
                          <div class="cmd-head">
                            <div class="cmd-icon" :class="step.type">
                              <!-- Think: lightbulb -->
                              <svg v-if="step.type === 'ai-step-think'" viewBox="0 0 24 24" fill="none" class="cmd-icon-svg">
                                <path d="M9.663 17h4.674M12 3v1m6.364 2.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a1 1 0 01-1 1h-2a1 1 0 01-1-1v-.53c0-1.067-.388-2.095-1.095-2.88l-.549-.547z" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
                              </svg>
                              <!-- Tool: terminal -->
                              <svg v-else-if="step.type === 'ai-step-tool'" viewBox="0 0 24 24" fill="none" class="cmd-icon-svg">
                                <rect x="3" y="4" width="18" height="16" rx="2" stroke="currentColor" stroke-width="1.5"/>
                                <path d="M7 9l3 3-3 3M12 15h4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
                              </svg>
                              <!-- Error: alert -->
                              <svg v-else-if="step.type === 'ai-step-error'" viewBox="0 0 24 24" fill="none" class="cmd-icon-svg">
                                <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.5"/>
                                <path d="M12 8v4M12 16h.01" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
                              </svg>
                              <!-- Result: sparkle -->
                              <svg v-else viewBox="0 0 24 24" fill="none" class="cmd-icon-svg">
                                <path d="M12 3l1.3 4.7L18 9l-4.7 1.3L12 15l-1.3-4.7L6 9l4.7-1.3z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/>
                                <path d="M17 16l.5 1.5L19 18l-1.5.5L17 20l-.5-1.5L15 18l1.5-.5z" stroke="currentColor" stroke-width="1.2" stroke-linejoin="round"/>
                              </svg>
                            </div>
                            <div class="cmd-head-text">
                              <span class="cmd-badge" :class="step.type">{{ stepBadgeLabel(step.type) }}</span>
                              <span class="cmd-num">{{ step.title || `Step ${step.stepNo}` }}</span>
                            </div>
                            <span v-if="step.eventStatus" class="cmd-status">{{ step.eventStatus }}</span>
                            <span v-if="step.duration" class="cmd-time">{{ step.duration }}</span>
                            <span v-if="stepIndex === (msg.trace?.steps?.length || 0) - 1 && msg.trace?.status === 'running' && !step.typing" class="cmd-live">LIVE</span>
                          </div>

                          <!-- Block body -->
                          <div class="cmd-body">
                            <!-- Tool: terminal-style -->
                            <div v-if="step.type === 'ai-step-tool'" class="cmd-tool">
                              <div class="cmd-tool-prompt">
                                <span class="prompt-chevron">&rsaquo;</span>
                                <span class="prompt-cmd">{{ getToolSummary(step.content) }}</span>
                              </div>
                              <button class="cmd-tool-expand" type="button" @click="toggleToolRaw(msg.id, step, stepIndex)">
                                <svg viewBox="0 0 16 16" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: isToolRawExpanded(msg.id, step, stepIndex) }">
                                  <path d="M3 6l5 4 5-4" stroke-linecap="round" stroke-linejoin="round"/>
                                </svg>
                                {{ isToolRawExpanded(msg.id, step, stepIndex) ? '收起输出' : '展开输出' }}
                              </button>
                              <Transition name="tool-reveal">
                                <div v-if="isToolRawExpanded(msg.id, step, stepIndex)" class="cmd-tool-output">
                                  <!-- Raw fallback -->
                                  <div v-if="classifyToolResult(step.content).kind === 'raw'">
                                    <pre class="cmd-tool-raw">{{ getToolRaw(step.content) }}</pre>
                                    <!-- File download buttons -->
                                    <div v-if="extractFilePaths(step.content).length" class="file-dloads">
                                      <div
                                        v-for="fp in extractFilePaths(step.content)"
                                        :key="fp"
                                        class="file-action-group"
                                      >
                                        <button
                                          v-if="isPreviewableFile(fp)"
                                          class="file-dload-btn"
                                          type="button"
                                          @click="openManusPreview(fp)"
                                        >
                                          <svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.5">
                                            <path d="M2.5 8s2-3.5 5.5-3.5S13.5 8 13.5 8s-2 3.5-5.5 3.5S2.5 8 2.5 8z" stroke-linecap="round" stroke-linejoin="round"/>
                                            <circle cx="8" cy="8" r="1.5"/>
                                          </svg>
                                          <span>预览</span>
                                        </button>
                                        <button
                                          class="file-dload-btn"
                                          type="button"
                                          @click="downloadManusFile(fp)"
                                        >
                                          <svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.5">
                                            <path d="M4 10v2h8v-2M8 2v8M5 7l3 3 3-3" stroke-linecap="round" stroke-linejoin="round"/>
                                          </svg>
                                          <span class="file-dload-name">{{ fp.split('/').pop() }}</span>
                                          <span class="file-dload-type">{{ (fp.split('.').pop() || '').toUpperCase() }}</span>
                                        </button>
                                      </div>
                                    </div>
                                  </div>

                                  <!-- Chart artifact with visual preview -->
                                  <div v-else-if="classifyToolResult(step.content).kind === 'chart'" class="chart-artifact">
                                    <div class="chart-artifact-head">
                                      <div>
                                        <div class="chart-artifact-title">{{ classifyToolResult(step.content).data.title || '图表分析' }}</div>
                                        <div class="chart-artifact-sub">{{ classifyToolResult(step.content).data.insight || '已生成图表分析文件' }}</div>
                                      </div>
                                      <span class="chart-artifact-type">{{ (classifyToolResult(step.content).data.chartType || 'chart').toUpperCase() }}</span>
                                    </div>
                                    <img
                                      v-if="getChartImagePath(classifyToolResult(step.content).data) && getPreviewUrl(getChartImagePath(classifyToolResult(step.content).data))"
                                      class="chart-artifact-img"
                                      :src="getPreviewUrl(getChartImagePath(classifyToolResult(step.content).data))"
                                      loading="lazy"
                                      @error="$event.target.style.display='none'"
                                    />
                                    <div class="artifact-grid">
                                      <div
                                        v-for="file in classifyToolResult(step.content).files"
                                        :key="file.path"
                                        class="artifact-card"
                                      >
                                        <div class="artifact-type">{{ artifactTypeLabel(file) }}</div>
                                        <div class="artifact-name">{{ file.label || artifactFileName(file.path) }}</div>
                                        <div class="artifact-actions">
                                          <button v-if="artifactPreviewable(file)" type="button" @click="openManusPreview(file.path, file)">预览</button>
                                          <button type="button" @click="downloadManusFile(file.path)">下载</button>
                                        </div>
                                      </div>
                                    </div>
                                  </div>

                                  <!-- Downloadable artifact bundle -->
                                  <div v-else-if="classifyToolResult(step.content).kind === 'artifacts'" class="artifact-grid">
                                    <div
                                      v-for="file in classifyToolResult(step.content).data"
                                      :key="file.path"
                                      class="artifact-card"
                                    >
                                      <div class="artifact-type">{{ artifactTypeLabel(file) }}</div>
                                      <div class="artifact-name">{{ file.label || artifactFileName(file.path) }}</div>
                                      <div class="artifact-path">{{ artifactFileName(file.path) }}</div>
                                      <div class="artifact-actions">
                                        <button v-if="artifactPreviewable(file)" type="button" @click="openManusPreview(file.path, file)">预览</button>
                                        <button type="button" @click="downloadManusFile(file.path)">下载</button>
                                      </div>
                                    </div>
                                  </div>

                                  <!-- Cards: search results / object array -->
                                  <div v-else-if="classifyToolResult(step.content).kind === 'cards'" class="viz-cards">
                                    <div v-for="(card, ci) in classifyToolResult(step.content).data" :key="ci" class="viz-card">
                                      <!-- Thumbnail -->
                                      <img
                                        v-if="pickThumbnail(card, classifyToolResult(step.content).keys)"
                                        :src="pickThumbnail(card, classifyToolResult(step.content).keys)"
                                        class="viz-card-thumb"
                                        loading="lazy"
                                        @error="$event.target.style.display='none'"
                                      />
                                      <!-- Position badge -->
                                      <div class="viz-card-num">{{ pickPosition(card, classifyToolResult(step.content).keys) || (ci + 1) }}</div>
                                      <!-- Main content -->
                                      <div class="viz-card-body">
                                        <div class="viz-card-title">{{ pickTitle(card, classifyToolResult(step.content).keys) || '#' + (ci + 1) }}</div>
                                        <div class="viz-card-source" v-if="pickSource(card, classifyToolResult(step.content).keys)">
                                          <svg viewBox="0 0 16 16" width="10" height="10" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="8" cy="8" r="6"/><path d="M6 8l1.5 1.5L10 7" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                          {{ pickSource(card, classifyToolResult(step.content).keys) }}
                                        </div>
                                        <div class="viz-card-desc" v-if="pickDesc(card, classifyToolResult(step.content).keys)">{{ pickDesc(card, classifyToolResult(step.content).keys) }}</div>
                                        <!-- Highlighted keywords -->
                                        <div class="viz-card-hl-words" v-if="pickHighlightedWords(card, classifyToolResult(step.content).keys).length">
                                          <span v-for="(hw, hi) in pickHighlightedWords(card, classifyToolResult(step.content).keys)" :key="hi" class="viz-card-hl">{{ hw }}</span>
                                        </div>
                                        <!-- Meta row: date + extra tags -->
                                        <div class="viz-card-meta">
                                          <span class="viz-card-date" v-if="pickDate(card, classifyToolResult(step.content).keys)">{{ pickDate(card, classifyToolResult(step.content).keys) }}</span>
                                          <span v-for="ex in pickExtra(card, classifyToolResult(step.content).keys)" :key="ex.key" class="viz-card-tag">{{ ex.key }}: {{ ex.val }}</span>
                                        </div>
                                      </div>
                                      <!-- External link as clickable text -->
                                      <a v-if="pickLink(card, classifyToolResult(step.content).keys)" :href="pickLink(card, classifyToolResult(step.content).keys)" target="_blank" rel="noopener" class="viz-card-link">
                                        <svg viewBox="0 0 16 16" width="12" height="12" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M6 3H3v10h10v-3M8 8l5.5-5.5M10 2h4v4" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                        <span>{{ pickLink(card, classifyToolResult(step.content).keys) }}</span>
                                      </a>
                                    </div>
                                  </div>

                                  <!-- Outline: generic object array — reading-list style -->
                                  <div v-else-if="classifyToolResult(step.content).kind === 'table'" class="viz-outline">
                                    <div v-for="(row, ri) in classifyToolResult(step.content).data.slice(0, 15)" :key="ri" class="outline-item">
                                      <div class="outline-bullet">{{ ri + 1 }}</div>
                                      <div class="outline-body">
                                        <div class="outline-title">{{ pickTitle(row, classifyToolResult(step.content).keys) || ('#' + (ri + 1)) }}</div>
                                        <div class="outline-rows">
                                          <div v-for="k in classifyToolResult(step.content).keys.filter(k => k !== pickTitle(row, classifyToolResult(step.content).keys) && typeof row[k] !== 'object').slice(0, 6)" :key="k" class="outline-row">
                                            <span class="outline-key">{{ humanizeKey(k) }}</span>
                                            <span class="outline-val" v-html="linkifyText(String(row[k] ?? ''))"></span>
                                          </div>
                                        </div>
                                      </div>
                                    </div>
                                  </div>

                                  <!-- Stats: flat key-value — humanized grid -->
                                  <div v-else-if="classifyToolResult(step.content).kind === 'stats'" class="viz-stats">
                                    <div v-for="([k, v], si) in classifyToolResult(step.content).data" :key="si" class="viz-stat">
                                      <span class="viz-stat-key">{{ humanizeKey(k) }}</span>
                                      <span class="viz-stat-val">{{ typeof v === 'object' ? JSON.stringify(v) : v }}</span>
                                    </div>
                                  </div>

                                  <!-- Outline-nested: nested objects as indented sections -->
                                  <div v-else-if="classifyToolResult(step.content).kind === 'blocks'" class="viz-outline">
                                    <div v-for="([k, v], bi) in classifyToolResult(step.content).data" :key="bi" class="outline-section">
                                      <div class="outline-section-head">{{ humanizeKey(k) }}</div>
                                      <div class="outline-section-body">
                                        <template v-if="Array.isArray(v) && v.length && typeof v[0] === 'object'">
                                          <div v-for="(item, ii) in v.slice(0, 8)" :key="ii" class="outline-item is-sub">
                                            <div class="outline-bullet sub">{{ ii + 1 }}</div>
                                            <div class="outline-body">
                                              <div class="outline-rows">
                                                <div v-for="(iv, ik) in Object.entries(item).slice(0, 6)" :key="ik" class="outline-row">
                                                  <span class="outline-key">{{ humanizeKey(iv[0]) }}</span>
                                                  <span class="outline-val" v-html="linkifyText(typeof iv[1] === 'object' ? JSON.stringify(iv[1]) : String(iv[1] ?? ''))"></span>
                                                </div>
                                              </div>
                                            </div>
                                          </div>
                                          <div v-if="v.length > 8" class="outline-more">… 还有 {{ v.length - 8 }} 条</div>
                                        </template>
                                        <template v-else-if="typeof v === 'object' && v !== null">
                                          <div v-for="(iv, ik) in Object.entries(v).slice(0, 8)" :key="ik" class="outline-row">
                                            <span class="outline-key">{{ humanizeKey(iv[0]) }}</span>
                                            <span class="outline-val" v-html="linkifyText(typeof iv[1] === 'object' ? JSON.stringify(iv[1]) : String(iv[1] ?? ''))"></span>
                                          </div>
                                        </template>
                                        <span v-else class="outline-text">{{ v }}</span>
                                      </div>
                                    </div>
                                  </div>

                                  <!-- Chips: primitive array -->
                                  <div v-else-if="classifyToolResult(step.content).kind === 'chips'" class="viz-chips">
                                    <span v-for="(chip, chi) in classifyToolResult(step.content).data" :key="chi" class="viz-chip">{{ chip }}</span>
                                  </div>
                                </div>
                              </Transition>
                            </div>
                            <!-- Non-tool: prose with structured title + body -->
                            <div v-else class="cmd-text">
                              <template v-if="extractStepParts(step.content).body">
                                <div class="cmd-text-title">{{ extractStepParts(step.content).title }}</div>
                                <div class="cmd-text-body" v-html="linkifyText(extractStepParts(step.content).body)"></div>
                              </template>
                              <template v-else>
                                <span v-html="linkifyText(step.content)"></span>
                              </template>
                              <span v-if="step.typing" class="cursor">▋</span>
                            </div>
                          </div>

                          <!-- Energy connector to next step -->
                          <div v-if="stepIndex < (msg.trace?.steps?.length || 0) - 1" class="cmd-connector" :class="{ flow: msg.trace?.status === 'running' && !(msg.trace?.steps?.[stepIndex + 1]?.typing) }">
                            <div class="connector-line"></div>
                            <div class="connector-dot"></div>
                          </div>
                        </div>
                      </TransitionGroup>
                    </div>
                  </div>
                </Transition>

                <!-- Final result -->
                <Transition name="trace-expand">
                  <div v-if="msg.trace?.finalResultMarkdown" class="trace-result">
                    <button
                      class="message-action-btn trace-copy-btn"
                      type="button"
                      :aria-label="isCopied(actionKey(msg, 'trace-copy')) ? '已复制' : '复制推理结果'"
                      :title="isCopied(actionKey(msg, 'trace-copy')) ? '已复制' : '复制全文'"
                      @click="copyMessageText(displayFinalResultMarkdown(msg.trace), actionKey(msg, 'trace-copy'))"
                    >
                      <svg v-if="!isCopied(actionKey(msg, 'trace-copy'))" viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.7">
                        <rect x="7" y="5" width="9" height="11" rx="2"/>
                        <path d="M4 13V6a2 2 0 0 1 2-2h7"/>
                      </svg>
                      <svg v-else viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8">
                        <path d="m4.5 10.5 3.4 3.4 7.6-8.1" stroke-linecap="round" stroke-linejoin="round"/>
                      </svg>
                    </button>
                    <div class="trace-result-label">
                      <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M12 3l1.3 4.7L18 9l-4.7 1.3L12 15l-1.3-4.7L6 9l4.7-1.3z" stroke-linejoin="round"/>
                      </svg>
                      推理结果
                    </div>
                    <div class="trace-result-body" v-html="renderMarkdown(displayFinalResultMarkdown(msg.trace))"></div>
                    <div v-if="traceReportArtifacts(msg.trace).length" class="trace-artifact-panel">
                      <div class="trace-artifact-title">报告文件</div>
                      <div class="artifact-grid">
                        <div
                          v-for="file in traceReportArtifacts(msg.trace)"
                          :key="file.path"
                          class="artifact-card"
                        >
                          <div class="artifact-type">{{ artifactTypeLabel(file) }}</div>
                          <div class="artifact-name">{{ file.label || artifactFileName(file.path) }}</div>
                          <div class="artifact-path">{{ artifactFileName(file.path) }}</div>
                          <div class="artifact-actions">
                            <button v-if="artifactPreviewable(file)" type="button" @click="openManusPreview(file.path, file)">预览</button>
                            <button type="button" @click="downloadManusFile(file.path)">下载</button>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </Transition>
              </div>
            </div>
          </template>

          <!-- AI plain message -->
          <template v-else-if="!msg.isUser">
            <div class="avatar-col">
              <AiAvatarFallback :type="aiType" />
            </div>
            <div class="bubble-col ai-bubble-col">
              <div class="bubble ai-bubble">
                <button
                  class="message-action-btn bubble-copy-btn"
                  type="button"
                  :aria-label="isCopied(actionKey(msg, 'ai-copy')) ? '已复制' : '复制回复'"
                  :title="isCopied(actionKey(msg, 'ai-copy')) ? '已复制' : '复制全文'"
                  @click="copyMessageText(msg.content, actionKey(msg, 'ai-copy'))"
                >
                  <svg v-if="!isCopied(actionKey(msg, 'ai-copy'))" viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.7">
                    <rect x="7" y="5" width="9" height="11" rx="2"/>
                    <path d="M4 13V6a2 2 0 0 1 2-2h7"/>
                  </svg>
                  <svg v-else viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8">
                    <path d="m4.5 10.5 3.4 3.4 7.6-8.1" stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                </button>
                <div class="bubble-text" v-html="withCursor(renderMarkdown(msg.content), connectionStatus === 'connecting' && index === messages.length - 1)"></div>
                <div class="bubble-time">{{ formatTime(msg.time) }}</div>
              </div>
            </div>
          </template>

          <!-- User message -->
          <template v-else>
            <div class="avatar-col user-avatar-col">
              <UserAvatarFallback
                :avatar-url="authStore.user?.avatarUrl || ''"
                :name="authStore.displayName"
                :seed="authStore.user?.username || authStore.displayName || 'current-user'"
                variant="brand"
              />
            </div>
            <div class="bubble-col user-bubble-col">
              <div class="user-message-stack">
                <div class="bubble user-bubble">
                  <div class="bubble-text">{{ msg.content }}</div>
                  <div class="bubble-time">{{ formatTime(msg.time) }}</div>
                </div>
                <div class="message-actions user-message-actions">
                  <button
                    class="message-action-btn"
                    type="button"
                    :aria-label="isCopied(actionKey(msg, 'user-copy')) ? '已复制' : '复制消息'"
                    :title="isCopied(actionKey(msg, 'user-copy')) ? '已复制' : '复制'"
                    @click="copyMessageText(msg.content, actionKey(msg, 'user-copy'))"
                  >
                    <svg v-if="!isCopied(actionKey(msg, 'user-copy'))" viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.7">
                      <rect x="7" y="5" width="9" height="11" rx="2"/>
                      <path d="M4 13V6a2 2 0 0 1 2-2h7"/>
                    </svg>
                    <svg v-else viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8">
                      <path d="m4.5 10.5 3.4 3.4 7.6-8.1" stroke-linecap="round" stroke-linejoin="round"/>
                    </svg>
                  </button>
                  <button
                    class="message-action-btn"
                    type="button"
                    aria-label="编辑消息"
                    title="编辑"
                    @click="editUserMessage(msg.content)"
                  >
                    <svg viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.7">
                      <path d="M11.8 4.2 15.8 8 8 15.8l-4.2.5.6-4.1 7.4-8Z" stroke-linejoin="round"/>
                      <path d="m10.5 5.6 3.9 3.8" stroke-linecap="round"/>
                    </svg>
                  </button>
                </div>
              </div>
            </div>
          </template>
        </div>
      </div>
    </section>

    <section class="input-section">
      <div class="input-bar">
        <textarea
          v-model="inputMessage"
          @keydown.enter.prevent="sendMessage"
          :placeholder="inputPlaceholder"
          class="input-field"
          :disabled="connectionStatus === 'connecting'"
          rows="3"
        ></textarea>
        <button
          @click="sendMessage"
          class="send-btn"
          :disabled="connectionStatus === 'connecting' || !inputMessage.trim()"
        >
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="22" y1="2" x2="11" y2="13"></line>
            <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
          </svg>
        </button>
      </div>
    </section>

    <Teleport to="body">
      <Transition name="preview-fade">
        <div v-if="previewFile" class="file-preview-overlay" @click.self="closeManusPreview">
          <div class="file-preview-dialog" role="dialog" aria-modal="true">
            <div class="file-preview-head">
              <div>
                <div class="file-preview-title">{{ previewFile.label || reportFileLabel(previewFile.path) }}</div>
                <div class="file-preview-path">{{ artifactFileName(previewFile.path) }}</div>
              </div>
              <div class="file-preview-actions">
                <button type="button" @click="downloadManusFile(previewFile.path)">下载</button>
                <button type="button" class="file-preview-close" aria-label="关闭预览" @click="closeManusPreview">
                  <svg viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M5 5l10 10M15 5L5 15" stroke-linecap="round"/>
                  </svg>
                </button>
              </div>
            </div>
            <div v-if="isMarkdownPreview" class="file-preview-markdown">
              <div v-if="markdownPreviewLoading" class="file-preview-state">正在加载 Markdown 预览...</div>
              <div v-else-if="markdownPreviewError" class="file-preview-state is-error">{{ markdownPreviewError }}</div>
              <div v-else class="file-preview-markdown-body" v-html="renderMarkdown(markdownPreviewText)"></div>
            </div>
            <iframe
              v-else
              class="file-preview-frame"
              :src="previewFrameUrl"
              :title="previewFile.label || artifactFileName(previewFile.path)"
            ></iframe>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import AiAvatarFallback from './AiAvatarFallback.vue'
import UserAvatarFallback from './UserAvatarFallback.vue'
import { renderMarkdown } from '@/utils/markdown'
import { downloadManusFile, getManusFileAccessUrl } from '@/api'
import { useAuthStore } from '@/stores/auth'

// 将光标插入到渲染 HTML 最后一个块级元素内部（紧跟文字）
const withCursor = (html, show) => {
  if (!show || !html) return html || ''
  // 匹配最后一个闭合的块级标签，在其前面插入光标
  const match = html.match(/(.*)(<\/(?:p|li|td|th|h[1-4]|div|pre|blockquote)>)(\s*)$/s)
  if (match) {
    return match[1] + '<span class="cursor">▋</span>' + match[2] + match[3]
  }
  return html + '<span class="cursor">▋</span>'
}

const props = defineProps({
  messages: { type: Array, default: () => [] },
  connectionStatus: { type: String, default: 'disconnected' },
  aiType: { type: String, default: 'default' }
})

const emit = defineEmits(['send-message', 'toggle-reasoning'])

const authStore = useAuthStore()
const inputMessage = ref('')
const messagesContainer = ref(null)
const expandedToolRawMap = ref({})
const previewFile = ref(null)
const previewFrameUrl = ref('')
const markdownPreviewText = ref('')
const markdownPreviewLoading = ref(false)
const markdownPreviewError = ref('')
const signedPreviewUrls = ref({})
const signingPreviewPaths = new Set()
const copiedActionKey = ref('')
const isMarkdownPreview = computed(() => /\.md$/i.test(previewFile.value?.path || ''))

const inputPlaceholder = computed(() => {
  return props.aiType === 'super' ? '给 AI 智能体发送消息...' : '给 AI 理财顾问发送消息...'
})

const sendMessage = () => {
  if (!inputMessage.value.trim()) return
  emit('send-message', inputMessage.value)
  inputMessage.value = ''
}

const actionKey = (msg, action) => `${msg?.id || msg?.time || String(msg?.content || '').slice(0, 24) || 'message'}-${action}`

const isCopied = (key) => copiedActionKey.value === key

const copyMessageText = async (text, key) => {
  const value = String(text || '').trim()
  if (!value) return
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(value)
    } else {
      const textarea = document.createElement('textarea')
      textarea.value = value
      textarea.setAttribute('readonly', '')
      textarea.style.position = 'fixed'
      textarea.style.left = '-9999px'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
    }
    copiedActionKey.value = key
    window.setTimeout(() => {
      if (copiedActionKey.value === key) copiedActionKey.value = ''
    }, 1200)
  } catch (error) {
    console.warn('复制失败', error)
  }
}

const editUserMessage = (content) => {
  inputMessage.value = String(content || '')
  nextTick(() => {
    const textarea = document.querySelector('.input-field')
    textarea?.focus()
    if (typeof textarea?.setSelectionRange === 'function') {
      textarea.setSelectionRange(inputMessage.value.length, inputMessage.value.length)
    }
  })
}

const formatTime = (timestamp) => {
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

const stepBadgeLabel = (type) => {
  const map = {
    'ai-step-think': '思考',
    'ai-step-tool': '工具调用',
    'ai-step-error': '错误',
    'ai-step-result': '结论'
  }
  return map[type] || '步骤'
}

// ── Trace header dynamic text ──
const traceHeaderText = (trace) => {
  if (!trace) return '推理过程'
  if (trace.status === 'running') return '正在推理...'
  if (trace.status === 'error') return '推理中断'
  const steps = trace.steps?.length || 0
  return `推理完成 · ${steps} 步`
}


// ── Structured tool result: classify & render visually ──
const isStructuredToolResult = (content) => {
  if (!content) return false
  const raw = getToolRaw(content)
  try {
    const json = JSON.parse(raw)
    return json && typeof json === 'object'
  } catch {
    return false
  }
}

const classifyToolResult = (content) => {
  const raw = getToolRaw(content)
  try {
    const json = JSON.parse(raw)
    if (json && typeof json === 'object' && !Array.isArray(json)) {
      const files = normalizeArtifactFiles(json)
      if (isChartArtifact(json)) {
        return { kind: 'chart', data: json, files }
      }
      if (files.length) {
        return { kind: 'artifacts', data: files, title: json.title || json.message || '生成文件' }
      }
    }
    if (Array.isArray(json) && json.length > 0) {
      const first = json[0]
      if (typeof first === 'object' && first !== null) {
        const keys = Object.keys(first)
        const hasTitle = keys.some(k => /^(title|name|标题|名称|fileName|query)$/i.test(k))
        const hasLink = keys.some(k => /^(url|link|href|链接|address|path)$/i.test(k))
        const hasDesc = keys.some(k => /^(snippet|desc|description|summary|摘要|content|body|text|概述)$/i.test(k))
        return { kind: hasTitle ? 'cards' : 'table', data: json.slice(0, 20), keys, cardFields: { title: hasTitle, link: hasLink, desc: hasDesc } }
      }
      return { kind: 'chips', data: json.slice(0, 30).map(String) }
    }
    if (json && typeof json === 'object' && !Array.isArray(json)) {
      const entries = Object.entries(json).filter(([,v]) => v !== null && v !== '').slice(0, 20)
      const hasNested = entries.some(([,v]) => typeof v === 'object')
      return { kind: hasNested ? 'blocks' : 'stats', data: entries }
    }
  } catch {}
  return { kind: 'raw', data: raw }
}

const normalizeArtifactFiles = (json) => {
  const files = []
  const pushFile = (file) => {
    if (!file) return
    const path = file.path || file.filePath || file.previewPath || file.pdfPath || file.markdownPath || file.docxPath
    if (!path) return
    if (!isReportArtifact({ path })) return
    if (files.some(f => f.path === path)) return
    files.push({
      label: file.label || file.name || file.title || artifactFileName(path),
      type: file.type || inferFileType(path),
      path,
      previewable: file.previewable
    })
  }

  if (Array.isArray(json.files)) {
    json.files.forEach(pushFile)
  }
  ;[
    ['pdfPath', 'PDF 正式报告', 'pdf', true],
    ['previewPath', 'HTML 预览', 'html', true],
    ['markdownPath', 'Markdown 报告', 'md', true],
    ['docxPath', 'Word 文档', 'docx', false]
  ].forEach(([key, label, type, previewable]) => {
    if (json[key]) pushFile({ label, type, path: json[key], previewable })
  })
  return files
}

const isChartArtifact = (json) => {
  return json?.type === 'chart' || !!json?.chartType || !!json?.svgPath
}

const inferFileType = (path) => {
  const ext = String(path || '').split('.').pop()
  return ext ? ext.toLowerCase() : 'file'
}

const artifactFileName = (path) => {
  return String(path || '').split('/').pop() || 'artifact'
}

const artifactTypeLabel = (file) => {
  return String(file?.type || inferFileType(file?.path)).toUpperCase()
}

const artifactPreviewable = (file) => {
  if (typeof file?.previewable === 'boolean') return file.previewable
  return isPreviewableFile(file?.path)
}

const openManusPreview = (path, file = null) => {
  if (!path) return
  previewFile.value = {
    label: file?.label || reportFileLabel(path),
    type: file?.type || inferFileType(path),
    path
  }
}

const closeManusPreview = () => {
  previewFile.value = null
}

watch(previewFile, async (file) => {
  previewFrameUrl.value = ''
  markdownPreviewText.value = ''
  markdownPreviewError.value = ''
  markdownPreviewLoading.value = false
  if (!file?.path) return

  const requestPath = file.path
  if (!/\.md$/i.test(file.path)) {
    try {
      previewFrameUrl.value = await getSignedPreviewUrl(requestPath)
    } catch (error) {
      markdownPreviewError.value = error?.message || '文件预览链接生成失败'
    }
    return
  }

  markdownPreviewLoading.value = true
  try {
    const url = await getSignedPreviewUrl(requestPath)
    const response = await fetch(url)
    if (!response.ok) {
      throw new Error(`Markdown 预览加载失败（${response.status}）`)
    }
    const text = await response.text()
    if (previewFile.value?.path === requestPath) {
      markdownPreviewText.value = text
    }
  } catch (error) {
    if (previewFile.value?.path === requestPath) {
      markdownPreviewError.value = error?.message || 'Markdown 预览加载失败'
    }
  } finally {
    if (previewFile.value?.path === requestPath) {
      markdownPreviewLoading.value = false
    }
  }
})

const isReportArtifact = (file) => {
  return /\.(md|pdf|html|htm|docx)$/i.test(file?.path || '')
}

const isPrimaryReportArtifact = (file) => {
  return /\.(md|pdf|docx)$/i.test(file?.path || '')
}

const reportFileLabel = (path) => {
  const type = inferFileType(path)
  if (type === 'pdf') return 'PDF 正式报告'
  if (type === 'html' || type === 'htm') return 'HTML 预览'
  if (type === 'md') return 'Markdown 报告'
  if (type === 'docx') return 'Word 文档'
  return artifactFileName(path)
}

const reportFileOrder = (file) => {
  const type = inferFileType(file?.path)
  const order = { md: 1, pdf: 2, html: 3, htm: 3, docx: 4 }
  return order[type] || 99
}

const parseToolResultJson = (content) => {
  const raw = getToolRaw(content)
  try {
    const json = JSON.parse(raw)
    return json && typeof json === 'object' ? json : null
  } catch {
    return null
  }
}

const isReportArtifactBundle = (json) => {
  if (!json || Array.isArray(json)) return false
  if (json.type === 'chart') return false
  if (json.type === 'artifact_bundle' || !!json.markdownPath || !!json.docxPath || !!json.pdfPath) return true
  return Array.isArray(json.files) && json.files.some(file => isPrimaryReportArtifact(file))
}

const traceReportArtifacts = (trace) => {
  const found = []
  const addFile = (file) => {
    if (!file?.path || !isReportArtifact(file)) return
    if (found.some(item => item.path === file.path)) return
    found.push(file)
  }
  ;(trace?.steps || []).forEach(step => {
    const json = parseToolResultJson(step.content || step.fullContent || '')
    if (!isReportArtifactBundle(json)) return
    normalizeArtifactFiles(json).forEach(addFile)
  })
  extractReportFilePaths(trace?.finalResultMarkdown || '').forEach(path => {
    addFile({
      label: reportFileLabel(path),
      type: inferFileType(path),
      path,
      previewable: isPreviewableFile(path)
    })
  })
  return found.sort((a, b) => reportFileOrder(a) - reportFileOrder(b))
}

const displayFinalResultMarkdown = (trace) => {
  const raw = trace?.finalResultMarkdown || ''
  const cleaned = stripReportFileList(raw)
  return cleaned || '报告已生成，可在下方预览或下载。'
}

const extractReportFilePaths = (content) => {
  if (!content) return []
  const raw = getToolRaw(content)
  const paths = []
  const directRe = /\/[^\s`'"<>)\],，；;]+?\.(?:md|pdf|html|htm|docx)\b/gi
  let match
  while ((match = directRe.exec(raw)) !== null) {
    const path = match[0]
    if (!paths.includes(path)) paths.push(path)
  }
  return paths
}

const stripReportFileList = (markdown) => {
  if (!markdown) return ''
  let text = markdown.replace(/\r\n/g, '\n').trim()

  const fileSectionIndex = text.search(/(^|\n)\s{0,3}(?:#{1,6}\s*)?(?:报告文件|文件下载|下载文件|生成文件|相关文件|附件|输出文件)\s*[:：]?\s*\n/i)
  if (fileSectionIndex >= 0 && extractReportFilePaths(text.slice(fileSectionIndex)).length) {
    text = text.slice(0, fileSectionIndex).trim()
  }

  const lines = text.split('\n')
  const kept = []
  let skippingFileBlock = false
  const fileTitleRe = /(?:PDF\s*正式报告|HTML\s*预览|Markdown\s*(?:文件|报告)?|Word\s*文档|DOCX\s*文档)/i
  const pathLineRe = /(?:文件)?路径\s*[:：]|\/[^\s`'"<>)\],，；;]+?\.(?:md|pdf|html|htm|docx)\b/i

  for (const line of lines) {
    const trimmed = line.trim()
    const startsFileBlock = fileTitleRe.test(trimmed) && /^(?:[-*]\s*)?(?:\d+[.、)]\s*)?/.test(trimmed)
    if (startsFileBlock) {
      skippingFileBlock = true
      continue
    }
    if (skippingFileBlock) {
      if (!trimmed) continue
      const looksLikeFileDetail = pathLineRe.test(trimmed)
        || /^[\-*o]\s+/.test(trimmed)
        || /^[-*]\s*(?:用途|适合|说明)\s*[:：]/.test(trimmed)
        || /(?:用途|适合|打印|预览|编辑|转换|文档|文件)/.test(trimmed)
      if (looksLikeFileDetail) continue
      skippingFileBlock = false
    }
    if (pathLineRe.test(trimmed)) continue
    if (/^如果您需要对这些文件做进一步的操作/.test(trimmed)) continue
    kept.push(line)
  }

  text = kept.join('\n')
    .replace(/我已(?:经)?(?:根据.*?要求)?生成(?:了)?(?:关于.*?)?(?:以下|如下)?(?:四种格式的)?文件[：:]?/g, '报告已生成。')
    .replace(/并提供了以下四种格式的文件[：:]?/g, '')
    .replace(/\n{3,}/g, '\n\n')
    .trim()

  return text
}

const getChartImagePath = (chart) => {
  return chart?.svgPath || chart?.path || ''
}

const getPreviewUrl = (filePath) => {
  if (!filePath) return ''
  if (!signedPreviewUrls.value[filePath]) {
    ensureSignedPreviewUrl(filePath)
  }
  return signedPreviewUrls.value[filePath] || ''
}

const ensureSignedPreviewUrl = (filePath) => {
  if (!filePath || signingPreviewPaths.has(filePath)) return
  signingPreviewPaths.add(filePath)
  getSignedPreviewUrl(filePath).finally(() => signingPreviewPaths.delete(filePath))
}

const getSignedPreviewUrl = async (filePath) => {
  if (signedPreviewUrls.value[filePath]) {
    return signedPreviewUrls.value[filePath]
  }
  const url = await getManusFileAccessUrl(filePath, 'preview')
  signedPreviewUrls.value = {
    ...signedPreviewUrls.value,
    [filePath]: url
  }
  return url
}

const cardFieldValue = (item, fieldNames) => {
  const key = fieldNames.find(k => item[k] !== undefined && item[k] !== null && item[k] !== '')
  return key ? String(item[key]) : ''
}

const humanizeKey = (k) => {
  return k
    .replace(/_/g, ' ')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/^[a-z]/, c => c.toUpperCase())
    .trim()
}

const pickTitle = (item, keys) => cardFieldValue(item, keys.filter(k => /^(title|name|标题|名称|fileName|query)$/i.test(k)).concat(keys))
const pickDesc = (item, keys) => cardFieldValue(item, keys.filter(k => /^(snippet|desc|description|summary|摘要|content|body|text|概述)$/i.test(k)))
const pickLink = (item, keys) => cardFieldValue(item, keys.filter(k => /^(url|link|href|链接|address|path)$/i.test(k)))
const pickSource = (item, keys) => cardFieldValue(item, keys.filter(k => /^(displayed_link|source|domain|来源|host|site)$/i.test(k)))
const pickThumbnail = (item, keys) => cardFieldValue(item, keys.filter(k => /^(thumbnail|image|img|icon|avatar|pic|photo|图片|缩略图)$/i.test(k)))
const pickDate = (item, keys) => cardFieldValue(item, keys.filter(k => /^(date|time|published|updated|日期|时间|publishedAt|createdAt)$/i.test(k)))
const pickPosition = (item, keys) => {
  const key = keys.find(k => /^(position|rank|index|no|排序|序号)$/i.test(k))
  if (key && item[key] !== undefined && item[key] !== null) {
    const n = Number(item[key])
    return Number.isFinite(n) ? n : null
  }
  return null
}
const pickHighlightedWords = (item, keys) => {
  const key = keys.find(k => /^(snippet_highlighted_words|highlighted|highlights|keywords|tags|labels)$/i.test(k))
  if (key && Array.isArray(item[key])) return item[key].slice(0, 10).map(String).filter(s => s)
  return []
}
const pickExtra = (item, keys) => {
  const used = new Set()
  keys.filter(k => /^(title|name|标题|名称|fileName|query)$/i.test(k)).forEach(k => used.add(k))
  keys.filter(k => /^(snippet|desc|description|summary|摘要|content|body|text|概述)$/i.test(k)).forEach(k => used.add(k))
  keys.filter(k => /^(url|link|href|链接|address|path)$/i.test(k)).forEach(k => used.add(k))
  keys.filter(k => /^(displayed_link|source|domain|来源|host|site)$/i.test(k)).forEach(k => used.add(k))
  keys.filter(k => /^(thumbnail|image|img|icon|avatar|pic|photo|图片|缩略图)$/i.test(k)).forEach(k => used.add(k))
  keys.filter(k => /^(date|time|published|updated|日期|时间|publishedAt|createdAt)$/i.test(k)).forEach(k => used.add(k))
  keys.filter(k => /^(snippet_highlighted_words|highlighted|highlights|keywords|tags|labels)$/i.test(k)).forEach(k => used.add(k))
  keys.filter(k => /^(position|rank|index|no|排序|序号)$/i.test(k)).forEach(k => used.add(k))
  return keys.filter(k => !used.has(k)).slice(0, 4).map(k => ({ key: k, val: String(item[k] ?? '') })).filter(e => e.val)
}

// ── Auto-linkify URLs in plain text ──
const linkifyText = (text) => {
  if (!text) return ''
  const value = String(text)
  const urlPattern = /(https?:\/\/[^\s<>"'；，。]+)/gi
  let output = ''
  let lastIndex = 0
  let match
  while ((match = urlPattern.exec(value)) !== null) {
    const url = match[0]
    output += escapeHtml(value.slice(lastIndex, match.index))
    output += `<a href="${escapeAttr(url)}" target="_blank" rel="noopener noreferrer" class="auto-link">${escapeHtml(url)}</a>`
    lastIndex = match.index + url.length
  }
  output += escapeHtml(value.slice(lastIndex))
  return output
}

const escapeHtml = (value) => String(value ?? '')
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')
  .replace(/'/g, '&#39;')

const escapeAttr = escapeHtml

// ── Extract title + body from step prose ──
const extractStepParts = (content) => {
  if (!content) return { title: '', body: '' }
  const trimmed = content.trim()
  const nlIdx = trimmed.indexOf('\n')
  if (nlIdx > 0 && nlIdx < 150) {
    return { title: trimmed.slice(0, nlIdx).trim(), body: trimmed.slice(nlIdx + 1).trim() }
  }
  if (trimmed.length < 80) return { title: trimmed, body: '' }
  return { title: trimmed, body: '' }
}

const getToolStepKey = (traceId, step, stepIndex) => `${traceId}-${step.stepNo}-${stepIndex}`

const isToolRawExpanded = (traceId, step, stepIndex) => {
  return !!expandedToolRawMap.value[getToolStepKey(traceId, step, stepIndex)]
}

const toggleToolRaw = (traceId, step, stepIndex) => {
  const key = getToolStepKey(traceId, step, stepIndex)
  expandedToolRawMap.value[key] = !expandedToolRawMap.value[key]
}

const getToolRaw = (content) => {
  if (!content) return ''
  const markers = ['返回的结果：', '返回的结果:', '返回的结果\n', '返回的结果']
  let payload = content
  let found = false
  for (const m of markers) {
    const idx = content.indexOf(m)
    if (idx >= 0) { payload = content.slice(idx + m.length); found = true; break }
  }
  if (!found) {
    const t = content.trim()
    if ((t.startsWith('{') || t.startsWith('[')) && (t.endsWith('}') || t.endsWith(']'))) { payload = t }
  }
  payload = payload.trim()
  if ((payload.startsWith('"') && payload.endsWith('"')) || (payload.startsWith("'") && payload.endsWith("'"))) {
    payload = payload.slice(1, -1)
  }
  return payload.replace(/\\n/g, '\n').replace(/\\t/g, '\t').replace(/\\"/g, '"').replace(/\\\\/g, '\\')
}

const extractFilePaths = (content) => {
  if (!content) return []
  const raw = getToolRaw(content)
  const paths = []
  const re = /(?:(?:generated|saved|written|created|输出|生成|保存)(?:\s+(?:successfully|to|文件|成功))?\s*(?:to|路径|file)?[:\s]*)\s*(\/[^\s,;\n，；]+(?:\.pdf|\.md|\.html|\.htm|\.docx))/gi
  let m
  while ((m = re.exec(raw)) !== null) {
    paths.push(m[1])
  }
  extractReportFilePaths(raw).forEach(p => {
    if (!paths.includes(p) && p.includes('/tmp/')) paths.push(p)
  })
  return paths.slice(0, 5)
}

const isPreviewableFile = (filePath) => {
  return /\.(md|pdf|html|htm)$/i.test(filePath || '')
}

const getToolSummary = (content) => {
  const raw = getToolRaw(content)
  if (!raw) return '工具返回空结果'
  try {
    const json = JSON.parse(raw)
    if (Array.isArray(json)) {
      const titles = json.slice(0, 3).map(item => item?.title || item?.name).filter(Boolean)
      return `返回 ${json.length} 条结果${titles.length ? '：' + titles.join('、') : ''}`
    }
    if (json && typeof json === 'object') {
      return `返回对象，字段：${Object.keys(json).slice(0, 5).join(', ')}`
    }
  } catch (e) { /* ignore */ }
  const compact = raw.replace(/\s+/g, ' ').trim()
  if (!compact) return '工具返回空结果'
  return compact.length > 120 ? `${compact.slice(0, 120)}...` : compact
}

// Auto-scroll
const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

watch(() => props.messages.length, () => { scrollToBottom() })
watch(() => props.messages.map(m => m.content).join(''), () => { scrollToBottom() })
watch(
  () => props.messages
    .filter(m => m.type === 'ai-trace')
    .map(m => {
      const steps = (m.trace?.steps || []).map(s => s.content).join('')
      return `${steps}::${m.trace?.finalResultMarkdown || ''}::${m.trace?.collapsed}`
    })
    .join('||'),
  () => { scrollToBottom() }
)
watch(() => props.messages.length, () => {
  expandedToolRawMap.value = {}
  scrollToBottom()
})
onMounted(() => { scrollToBottom() })
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

/* ── Messages Area ── */
.chat-display-panel {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.chat-messages {
  height: 100%;
  overflow-y: auto;
  padding: 12px 0;
  display: flex;
  flex-direction: column;
  gap: 24px;
  scroll-behavior: smooth;
}

/* ── Message Row ── */
.message-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  max-width: 95%;
}

.message-row.is-user {
  margin-left: auto;
  flex-direction: row-reverse;
}

.avatar-col {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  margin-top: 2px;
}

.user-avatar-col {
  margin-top: 2px;
}

.bubble-col {
  min-width: 0;
  max-width: 100%;
}

.ai-bubble-col {
  min-width: 0;
  width: 95%;
}

.trace-bubble-col {
  min-width: 0;
  width: 95%;
}

.user-bubble-col {
  display: flex;
  justify-content: flex-end;
}

.user-message-stack {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

/* ── Bubbles ── */
.bubble {
  padding: 12px 16px;
  border-radius: 18px;
  word-break: break-word;
  position: relative;
}

.ai-bubble {
  background: var(--bg-surface);
  border: 1px solid var(--line-soft);
  color: var(--text-main);
  border-bottom-left-radius: 4px;
  max-width: 100%;
  padding-right: 42px;
}

.user-bubble {
  background: #f5f3ff;
  color: #111827;
  border: 1px solid #ddd6fe;
  border-bottom-right-radius: 4px;
}

.bubble-text {
  font-size: 0.95rem;
  line-height: 1.7;
  white-space: pre-wrap;
}

.ai-bubble .bubble-text {
  white-space: normal;
}

.ai-bubble .bubble-text :deep(h1),
.ai-bubble .bubble-text :deep(h2),
.ai-bubble .bubble-text :deep(h3) {
  margin: 10px 0 6px;
  color: var(--text-strong);
  font-weight: 700;
  line-height: 1.35;
}

.ai-bubble .bubble-text :deep(h1) { font-size: 1.1rem; }
.ai-bubble .bubble-text :deep(h2) { font-size: 1.02rem; }
.ai-bubble .bubble-text :deep(h3) { font-size: 0.95rem; }
.ai-bubble .bubble-text :deep(h4) { font-size: 0.9rem; }

.ai-bubble .bubble-text :deep(p) {
  margin: 5px 0;
}

.ai-bubble .bubble-text :deep(ul),
.ai-bubble .bubble-text :deep(ol) {
  margin: 8px 0 10px 20px;
  padding: 0;
  line-height: 1.8;
}

.ai-bubble .bubble-text :deep(ol) {
  list-style-type: decimal;
}

.ai-bubble .bubble-text :deep(ul) {
  list-style-type: disc;
}

.ai-bubble .bubble-text :deep(li) {
  margin: 4px 0;
  padding-left: 4px;
}

.ai-bubble .bubble-text :deep(li::marker) {
  color: var(--brand);
  font-weight: 600;
}

.ai-bubble .bubble-text :deep(.sub-list) {
  margin: 4px 0 4px 16px;
  list-style-type: disc;
}

.ai-bubble .bubble-text :deep(.sub-list li) {
  margin: 2px 0;
  font-size: 0.92em;
  color: var(--text-main);
}

.ai-bubble .bubble-text :deep(.sub-list li::marker) {
  color: var(--text-muted);
  font-weight: 400;
}

.ai-bubble .bubble-text :deep(code) {
  font-family: var(--font-display);
  font-size: 0.84em;
  background: #f5f3ff;
  border: 1px solid #ddd6fe;
  border-radius: 4px;
  padding: 1px 5px;
  color: #7c3aed;
}

.ai-bubble .bubble-text :deep(pre) {
  margin: 8px 0;
  padding: 12px;
  background: #f8fafc;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  overflow-x: auto;
}

.ai-bubble .bubble-text :deep(pre code) {
  background: none;
  border: none;
  padding: 0;
  color: var(--text-main);
  font-size: 0.82rem;
}

.ai-bubble .bubble-text :deep(blockquote) {
  margin: 8px 0;
  padding: 8px 12px;
  border-left: 3px solid var(--brand);
  background: var(--accent-soft);
  border-radius: 6px;
  color: var(--text-main);
}

.ai-bubble .bubble-text :deep(strong) {
  font-weight: 700;
  color: var(--text-strong);
}

.ai-bubble .bubble-text :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 8px 0;
  font-size: 0.88rem;
}

.ai-bubble .bubble-text :deep(th),
.ai-bubble .bubble-text :deep(td) {
  border: 1px solid var(--line-soft);
  padding: 6px 10px;
  text-align: left;
}

.ai-bubble .bubble-text :deep(th) {
  background: #f5f3ff;
  font-weight: 600;
  color: var(--text-strong);
}

.ai-bubble .bubble-text :deep(tr:nth-child(even)) {
  background: var(--bg-surface-soft);
}

.ai-bubble .bubble-text :deep(img) {
  display: block;
  max-width: min(100%, 520px);
  max-height: 320px;
  object-fit: contain;
  margin: 12px 0;
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  background: #f8fafc;
}

.bubble-time {
  font-size: 0.7rem;
  opacity: 0.45;
  margin-top: 5px;
  text-align: right;
  color: var(--text-muted);
}

.user-bubble .bubble-time {
  color: #6b7280;
}

.message-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.user-message-actions {
  padding-right: 6px;
}

.message-action-btn {
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #8a8497;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  cursor: pointer;
  transition: color 0.15s, background 0.15s, transform 0.15s, opacity 0.15s;
}

.message-action-btn:hover {
  background: #f5f3ff;
  color: var(--brand);
}

.message-action-btn:active {
  transform: translateY(1px);
}

.bubble-copy-btn {
  position: absolute;
  top: 8px;
  right: 8px;
  opacity: 0.62;
}

.ai-bubble:hover .bubble-copy-btn,
.bubble-copy-btn:focus-visible {
  opacity: 1;
}

.cursor {
  display: inline-block;
  animation: blink 0.8s step-end infinite;
  color: var(--brand);
  margin-left: 1px;
}

@keyframes blink {
  50% { opacity: 0; }
}

/* ══════════════════════════════════════════════
   Trace Card — Neural Command Center Redesign
   ══════════════════════════════════════════════ */

.trace-card {
  background: var(--bg-surface);
  border: 1px solid var(--line-soft);
  border-radius: 18px;
  overflow: hidden;
  width: 100%;
  transition: border-color 0.35s, box-shadow 0.35s;
}

.trace-card.is-running {
  border-color: #c4b5fd;
  box-shadow: 0 0 0 3px rgba(124, 58, 237, 0.06), 0 4px 24px rgba(124, 58, 237, 0.04);
}

.trace-card.is-done {
  border-color: var(--line-soft);
}

.trace-card.is-error {
  border-color: #fca5a5;
  box-shadow: 0 0 0 2px rgba(239, 68, 68, 0.06);
}

/* ── Header ── */
.trace-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  cursor: pointer;
  transition: background 0.2s;
  user-select: none;
}

.trace-header:hover {
  background: var(--bg-surface-soft);
}

.trace-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* Brain-wave equalizer */
.brain-wave {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 22px;
  flex-shrink: 0;
}

.wave-bar {
  width: 3px;
  border-radius: 2px;
  background: var(--brand);
  animation: waveBounce 0.8s ease-in-out infinite alternate;
  transform-origin: bottom;
}

.brain-wave.idle .wave-bar {
  background: #22c55e;
  animation: none;
}

.brain-wave.error .wave-bar {
  background: #ef4444;
  animation: none;
}

@keyframes waveBounce {
  0%   { transform: scaleY(0.35); opacity: 0.5; }
  100% { transform: scaleY(1);    opacity: 1; }
}

.trace-label {
  font-size: 0.84rem;
  font-weight: 650;
  color: var(--text-strong);
  letter-spacing: -0.01em;
}

.trace-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.trace-step-count {
  font-size: 0.7rem;
  font-weight: 600;
  color: var(--brand-strong);
  background: #f5f3ff;
  padding: 3px 10px;
  border-radius: 999px;
  font-family: var(--font-display);
}

.trace-duration {
  font-size: 0.7rem;
  color: var(--text-muted);
  font-family: var(--font-display);
}

.trace-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  color: var(--text-muted);
  transition: color 0.15s, background 0.15s;
}

.trace-toggle:hover {
  background: var(--line-soft);
  color: var(--text-strong);
}

.trace-toggle svg {
  transition: transform 0.3s var(--ease-smooth);
}

.trace-toggle svg.rotated {
  transform: rotate(180deg);
}

/* ── Waiting: morphing dots ── */
.trace-waiting {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 0 10px;
}

.morphing-dots {
  display: flex;
  gap: 5px;
  flex-shrink: 0;
}

.mdot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--brand);
  animation: morphDot 1.2s ease-in-out infinite;
}

@keyframes morphDot {
  0%, 100% { transform: scale(0.6); opacity: 0.35; }
  40%      { transform: scale(1.3); opacity: 1; }
  70%      { transform: scale(0.8); opacity: 0.7; }
}

.waiting-text {
  font-size: 0.85rem;
  color: var(--text-muted);
  font-style: italic;
}

/* ── Trace Body ── */
.trace-body {
  padding: 0 18px 16px;
  max-height: 420px;
  overflow-y: auto;
}

.command-list {
  display: flex;
  flex-direction: column;
}

/* ── Command Block ── */
.cmd-block {
  position: relative;
  border-radius: 12px;
  padding: 14px 16px;
  transition: box-shadow 0.3s, background 0.3s;
}

.cmd-block.ai-step-think {
  background: linear-gradient(135deg, #faf9ff, #f5f3ff);
  border: 1px solid #e9e3fc;
}

.cmd-block.ai-step-tool {
  background: linear-gradient(135deg, #fffbeb, #fefce8);
  border: 1px solid #fde68a;
}

.cmd-block.ai-step-result {
  background: linear-gradient(135deg, #f0fdf6, #ecfdf5);
  border: 1px solid #a7f3d0;
}

.cmd-block.ai-step-error {
  background: linear-gradient(135deg, #fff5f5, #fef2f2);
  border: 1px solid #fecaca;
}

.cmd-block.is-latest.ai-step-think {
  box-shadow: 0 0 0 2px rgba(124, 58, 237, 0.1);
}

.cmd-block.is-latest.ai-step-tool {
  box-shadow: 0 0 0 2px rgba(245, 158, 11, 0.12);
}

/* ── Command Head ── */
.cmd-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.cmd-icon {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.cmd-icon.ai-step-think {
  background: #ede9fe;
  color: #7c3aed;
}

.cmd-icon.ai-step-tool {
  background: #fef3c7;
  color: #d97706;
}

.cmd-icon.ai-step-result {
  background: #d1fae5;
  color: #059669;
}

.cmd-icon.ai-step-error {
  background: #fee2e2;
  color: #dc2626;
}

.cmd-icon-svg {
  width: 16px;
  height: 16px;
  display: block;
}

.cmd-head-text {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 0;
}

.cmd-badge {
  font-size: 0.68rem;
  font-weight: 700;
  padding: 3px 9px;
  border-radius: 999px;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  flex-shrink: 0;
}

.cmd-badge.ai-step-think { background: #ede9fe; color: #6d28d9; }
.cmd-badge.ai-step-tool  { background: #fef3c7; color: #b45309; }
.cmd-badge.ai-step-result{ background: #d1fae5; color: #047857; }
.cmd-badge.ai-step-error { background: #fee2e2; color: #b91c1c; }

.cmd-num {
  font-size: 0.7rem;
  color: var(--text-muted);
  font-family: var(--font-display);
}

.cmd-time {
  font-size: 0.66rem;
  color: var(--text-muted);
  font-family: var(--font-display);
  margin-left: auto;
}

.cmd-status {
  font-size: 0.62rem;
  font-weight: 700;
  color: #2563eb;
  background: #dbeafe;
  padding: 2px 7px;
  border-radius: 999px;
  font-family: var(--font-display);
}

.cmd-live {
  font-size: 0.58rem;
  font-weight: 700;
  color: #f59e0b;
  background: #fef3c7;
  padding: 2px 7px;
  border-radius: 999px;
  letter-spacing: 0.06em;
  animation: livePulse 1.5s ease-in-out infinite;
}

@keyframes livePulse {
  0%, 100% { opacity: 1; }
  50%      { opacity: 0.5; }
}

/* ── Command Body ── */
.cmd-body {
  padding-left: 38px;
}

.cmd-text {
  font-size: 0.86rem;
  line-height: 1.68;
  color: var(--text-main);
  white-space: pre-wrap;
}

.cmd-text-title {
  font-size: 0.92rem;
  font-weight: 700;
  color: var(--text-strong);
  margin-bottom: 6px;
  line-height: 1.4;
}

.cmd-text-body {
  color: var(--text-main);
  white-space: pre-wrap;
}

/* Auto-linked URLs in text */
:deep(.auto-link) {
  color: #7c3aed;
  text-decoration: underline;
  text-underline-offset: 2px;
  word-break: break-all;
}

:deep(.auto-link:hover) {
  color: #5b21b6;
}

/* ── Tool: terminal-style ── */
.cmd-tool {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cmd-tool-prompt {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 13px;
  background: #1e1b2e;
  border-radius: 8px;
  font-family: var(--font-display);
}

.prompt-chevron {
  color: #a78bfa;
  font-weight: 700;
  font-size: 1rem;
  line-height: 1;
  flex-shrink: 0;
  user-select: none;
}

.prompt-cmd {
  color: #e2e8f0;
  font-size: 0.78rem;
  line-height: 1.55;
  word-break: break-all;
}

.cmd-tool-expand {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  align-self: flex-start;
  font-size: 0.7rem;
  font-weight: 600;
  color: #b45309;
  background: #fef3c7;
  border-radius: 999px;
  padding: 3px 12px;
  transition: background 0.15s;
}

.cmd-tool-expand:hover {
  background: #fde68a;
}

.cmd-tool-expand svg {
  transition: transform 0.25s;
}

.cmd-tool-expand svg.rotated {
  transform: rotate(180deg);
}

/* ── Tool output ── */
.cmd-tool-output {
  overflow: hidden;
  padding-top: 2px;
}

.cmd-tool-raw {
  margin: 0;
  padding: 12px 14px;
  border-radius: 8px;
  background: #faf9fb;
  border: 1px solid #e9e3fc;
  color: var(--text-main);
  font-family: var(--font-display);
  font-size: 0.74rem;
  line-height: 1.55;
  white-space: pre;
  overflow: auto;
  max-height: 200px;
}

.file-dloads {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.file-action-group {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.file-dload-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 13px;
  border-radius: 8px;
  border: 1px solid #d4c8f5;
  background: #f5f0ff;
  color: #5b21b6;
  font-family: var(--font-body);
  font-size: 0.78rem;
  cursor: pointer;
  transition: all 0.18s;
}

.file-dload-btn:hover {
  background: #ede5ff;
  border-color: #a78bfa;
  color: #4c1d95;
}

.file-dload-btn svg {
  flex-shrink: 0;
  color: #7c3aed;
}

.file-dload-name {
  font-weight: 500;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-dload-type {
  font-family: var(--font-display);
  font-size: 0.64rem;
  text-transform: uppercase;
  color: #9b7cf4;
  background: #ede5ff;
  padding: 1px 5px;
  border-radius: 3px;
  letter-spacing: 0.03em;
}

/* ── Generated artifacts ── */
.artifact-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 10px;
}

.artifact-card {
  min-width: 0;
  padding: 12px;
  border-radius: 8px;
  border: 1px solid #dbeafe;
  background: #f8fbff;
}

.artifact-type {
  width: fit-content;
  padding: 2px 7px;
  border-radius: 4px;
  background: #dbeafe;
  color: #1d4ed8;
  font-family: var(--font-display);
  font-size: 0.64rem;
  font-weight: 700;
}

.artifact-name {
  margin-top: 8px;
  color: var(--text-strong);
  font-size: 0.82rem;
  font-weight: 650;
  line-height: 1.35;
}

.artifact-path {
  margin-top: 4px;
  color: var(--text-muted);
  font-family: var(--font-display);
  font-size: 0.68rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.artifact-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.artifact-actions button {
  padding: 6px 10px;
  border-radius: 7px;
  border: 1px solid #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 0.74rem;
  cursor: pointer;
}

.artifact-actions button:hover {
  background: #dbeafe;
  border-color: #93c5fd;
}

.trace-artifact-panel .artifact-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.trace-artifact-panel .artifact-card {
  padding: 12px;
}

.trace-artifact-panel .artifact-name,
.trace-artifact-panel .artifact-path {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chart-artifact {
  padding: 12px;
  border-radius: 8px;
  border: 1px solid #d1fae5;
  background: #f7fefb;
}

.chart-artifact-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.chart-artifact-title {
  color: var(--text-strong);
  font-size: 0.9rem;
  font-weight: 700;
}

.chart-artifact-sub {
  margin-top: 3px;
  color: var(--text-muted);
  font-size: 0.74rem;
  line-height: 1.5;
}

.chart-artifact-type {
  padding: 2px 7px;
  border-radius: 4px;
  background: #d1fae5;
  color: #047857;
  font-family: var(--font-display);
  font-size: 0.64rem;
  font-weight: 700;
}

.chart-artifact-img {
  display: block;
  width: 100%;
  max-height: 320px;
  object-fit: contain;
  border: 1px solid #d1fae5;
  border-radius: 8px;
  background: #fff;
  margin-bottom: 10px;
}

/* ═══ Visual Data Displays ═══ */

/* ── Cards (search results / object array) ── */
.viz-cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.viz-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px;
  background: #faf9fb;
  border: 1px solid #e9e3fc;
  border-radius: 10px;
  transition: background 0.15s, border-color 0.15s;
  position: relative;
}

.viz-card:hover {
  background: #f5f3ff;
  border-color: #c4b5fd;
}

.viz-card-thumb {
  width: 52px;
  height: 52px;
  border-radius: 6px;
  object-fit: cover;
  flex-shrink: 0;
  background: #ede9fe;
  border: 1px solid #e9e3fc;
}

.viz-card-num {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: #ede9fe;
  color: #7c3aed;
  font-size: 0.66rem;
  font-weight: 700;
  font-family: var(--font-display);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.viz-card-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.viz-card-title {
  font-size: 0.83rem;
  font-weight: 650;
  color: var(--text-strong);
  line-height: 1.35;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.viz-card-source {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 0.66rem;
  color: #16a34a;
  font-family: var(--font-display);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.viz-card-desc {
  font-size: 0.76rem;
  color: var(--text-muted);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.viz-card-hl-words {
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
  margin-top: 1px;
}

.viz-card-hl {
  font-size: 0.62rem;
  font-weight: 600;
  color: #b45309;
  background: #fef3c7;
  padding: 1px 7px;
  border-radius: 4px;
  white-space: nowrap;
}

.viz-card-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
}

.viz-card-date {
  font-size: 0.66rem;
  color: var(--text-muted);
  font-family: var(--font-display);
  display: flex;
  align-items: center;
  gap: 3px;
}

.viz-card-tag {
  font-size: 0.64rem;
  font-family: var(--font-display);
  color: #6d28d9;
  background: #ede9fe;
  padding: 1px 7px;
  border-radius: 4px;
  white-space: nowrap;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.viz-card-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-top: 6px;
  font-size: 0.72rem;
  font-family: var(--font-display);
  color: #7c3aed;
  text-decoration: underline;
  text-underline-offset: 2px;
  word-break: break-all;
  transition: color 0.15s;
  width: fit-content;
}

.viz-card-link:hover {
  color: #5b21b6;
}

.viz-card-link svg {
  flex-shrink: 0;
  color: #a78bfa;
}

/* ── Table (generic object array) ── */
/* ── Outline (natural reading format for arrays & nested objects) ── */
.viz-outline {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.outline-item {
  display: flex;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #f3f0f9;
}

.outline-item:last-child {
  border-bottom: none;
}

.outline-item.is-sub {
  padding: 6px 0 6px 12px;
  border-bottom: 1px dashed #f0edf7;
}

.outline-item.is-sub:last-child {
  border-bottom: none;
}

.outline-bullet {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #ede9fe;
  color: #7c3aed;
  font-size: 0.64rem;
  font-weight: 700;
  font-family: var(--font-display);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 1px;
}

.outline-bullet.sub {
  width: 18px;
  height: 18px;
  font-size: 0.58rem;
  background: #f5f3ff;
  color: #a78bfa;
}

.outline-body {
  flex: 1;
  min-width: 0;
}

.outline-title {
  font-size: 0.84rem;
  font-weight: 650;
  color: var(--text-strong);
  line-height: 1.4;
  margin-bottom: 3px;
}

.outline-rows {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.outline-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 0.78rem;
  line-height: 1.6;
}

.outline-key {
  font-size: 0.68rem;
  font-weight: 600;
  color: #6d28d9;
  font-family: var(--font-display);
  flex-shrink: 0;
  min-width: 64px;
}

.outline-key::after {
  content: ':';
  color: #c4b5fd;
}

.outline-val {
  color: var(--text-main);
  word-break: break-word;
}

/* ── Outline sections (nested objects) ── */
.outline-section {
  border-bottom: 1px solid #f3f0f9;
  padding: 8px 0;
}

.outline-section:last-child {
  border-bottom: none;
}

.outline-section-head {
  font-size: 0.74rem;
  font-weight: 700;
  color: #7c3aed;
  font-family: var(--font-display);
  margin-bottom: 4px;
}

.outline-section-body {
  padding-left: 8px;
}

.outline-more {
  font-size: 0.7rem;
  color: #a78bfa;
  font-weight: 600;
  padding: 4px 0 0 28px;
}

.outline-text {
  font-size: 0.82rem;
  color: var(--text-main);
  font-family: var(--font-display);
  line-height: 1.6;
}

/* ── Stats (flat key-value objects, humanized) ── */
.viz-stats {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 6px;
}

.viz-stat {
  padding: 10px 13px;
  background: #faf9fb;
  border: 1px solid #e9e3fc;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  transition: background 0.15s;
}

.viz-stat:hover {
  background: #f5f3ff;
}

.viz-stat-key {
  font-size: 0.63rem;
  font-weight: 650;
  color: #6d28d9;
  font-family: var(--font-display);
}

.viz-stat-val {
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--text-strong);
  word-break: break-word;
}

/* ── Chips (primitive arrays) ── */
.viz-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.viz-chip {
  font-size: 0.74rem;
  font-family: var(--font-display);
  color: #374151;
  background: #faf9fb;
  border: 1px solid #e9e3fc;
  border-radius: 6px;
  padding: 4px 10px;
  white-space: nowrap;
  transition: background 0.12s;
}

.viz-chip:hover {
  background: #f5f3ff;
  border-color: #c4b5fd;
}

/* ── Connector between command blocks ── */
.cmd-connector {
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 18px;
  position: relative;
  margin-left: 15px;
}

.connector-line {
  width: 2px;
  flex: 1;
  background: var(--line-soft);
  border-radius: 1px;
}

.connector-dot {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #c4b5fd;
}

.cmd-connector.flow .connector-dot {
  background: var(--brand);
  animation: flowPulse 1.2s ease-in-out infinite;
}

.cmd-connector.flow .connector-line {
  background: linear-gradient(180deg, var(--brand), #c4b5fd);
}

@keyframes flowPulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(124, 58, 237, 0.4); transform: translateY(-50%) scale(1); }
  50%      { box-shadow: 0 0 0 5px rgba(124, 58, 237, 0);   transform: translateY(-50%) scale(1.8); }
}

/* ── Final Result ── */
.trace-result {
  border-top: 1px solid var(--line-soft);
  padding: 18px 48px 18px 18px;
  background: linear-gradient(180deg, #faf9ff 0%, var(--bg-surface) 40%);
  position: relative;
}

.trace-copy-btn {
  position: absolute;
  top: 12px;
  right: 12px;
}

.trace-result-label {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 0.72rem;
  font-weight: 700;
  color: #7c3aed;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  margin-bottom: 12px;
  padding: 4px 12px;
  background: #ede9fe;
  border-radius: 999px;
}

.trace-result-body {
  font-size: 0.92rem;
  line-height: 1.78;
  color: var(--text-strong);
}

.trace-result-body :deep(h1),
.trace-result-body :deep(h2),
.trace-result-body :deep(h3) {
  margin: 12px 0 6px;
  color: var(--text-strong);
  font-weight: 700;
  line-height: 1.35;
}

.trace-result-body :deep(h1) { font-size: 1.15rem; }
.trace-result-body :deep(h2) { font-size: 1.05rem; }
.trace-result-body :deep(h3) { font-size: 0.95rem; }

.trace-result-body :deep(p) { margin: 6px 0; }
.trace-result-body :deep(ul),
.trace-result-body :deep(ol) { margin: 6px 0 8px 20px; padding: 0; line-height: 1.7; }
.trace-result-body :deep(li) { margin: 3px 0; }
.trace-result-body :deep(code) {
  font-family: var(--font-display);
  font-size: 0.84em;
  background: #f5f3ff;
  border: 1px solid #ddd6fe;
  border-radius: 4px;
  padding: 1px 5px;
  color: #7c3aed;
}
.trace-result-body :deep(pre) {
  margin: 8px 0; padding: 12px;
  background: #f8fafc;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  overflow-x: auto;
}
.trace-result-body :deep(pre code) { background: none; border: none; padding: 0; color: var(--text-main); font-size: 0.82rem; }
.trace-result-body :deep(blockquote) {
  margin: 8px 0; padding: 8px 12px;
  border-left: 3px solid var(--brand);
  background: var(--accent-soft);
  border-radius: 6px;
  color: var(--text-main);
}
.trace-result-body :deep(strong) { font-weight: 700; color: var(--text-strong); }
.trace-result-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 8px 0;
  font-size: 0.88rem;
}
.trace-result-body :deep(th),
.trace-result-body :deep(td) {
  border: 1px solid var(--line-soft);
  padding: 6px 10px;
  text-align: left;
}
.trace-result-body :deep(th) {
  background: #f5f3ff;
  font-weight: 600;
  color: var(--text-strong);
}

.trace-result-body :deep(img) {
  display: block;
  max-width: min(100%, 560px);
  max-height: 340px;
  object-fit: contain;
  margin: 12px 0;
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  background: #f8fafc;
}

.trace-artifact-panel {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid #e9e3fc;
}

.trace-artifact-title {
  margin-bottom: 10px;
  color: var(--text-strong);
  font-size: 0.86rem;
  font-weight: 700;
}

/* ── In-app file preview ── */
.file-preview-overlay {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.42);
  backdrop-filter: blur(8px);
}

.file-preview-dialog {
  width: min(1120px, 94vw);
  height: min(780px, 88vh);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 12px;
  border: 1px solid #dbeafe;
  background: #ffffff;
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.22);
}

.file-preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  border-bottom: 1px solid #e2e8f0;
  background: #f8fbff;
}

.file-preview-title {
  color: var(--text-strong);
  font-size: 0.92rem;
  font-weight: 700;
}

.file-preview-path {
  margin-top: 3px;
  max-width: 560px;
  overflow: hidden;
  color: var(--text-muted);
  font-family: var(--font-display);
  font-size: 0.72rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-preview-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.file-preview-actions button {
  height: 32px;
  padding: 0 11px;
  border-radius: 8px;
  border: 1px solid #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 0.76rem;
  cursor: pointer;
}

.file-preview-actions button:hover {
  background: #dbeafe;
  border-color: #93c5fd;
}

.file-preview-close {
  width: 32px;
  padding: 0 !important;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.file-preview-frame {
  flex: 1;
  width: 100%;
  min-height: 0;
  border: 0;
  background: #fff;
}

.file-preview-markdown {
  flex: 1;
  min-height: 0;
  overflow: auto;
  background: #eef2f7;
}

.file-preview-markdown-body {
  max-width: 980px;
  margin: 24px auto;
  padding: 42px 52px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
  color: #172033;
  font-size: 0.95rem;
  line-height: 1.85;
}

.file-preview-markdown-body :deep(h1) {
  margin: 0 0 22px;
  padding-bottom: 18px;
  border-bottom: 1px solid #e2e8f0;
  color: #172033;
  font-size: 1.9rem;
  line-height: 1.25;
}

.file-preview-markdown-body :deep(h2) {
  margin: 28px 0 12px;
  color: #1e3a8a;
  font-size: 1.22rem;
}

.file-preview-markdown-body :deep(h3) {
  margin: 22px 0 10px;
  color: #334155;
  font-size: 1.05rem;
}

.file-preview-markdown-body :deep(p) {
  margin: 10px 0;
}

.file-preview-markdown-body :deep(blockquote) {
  margin: 12px 0 20px;
  padding: 10px 14px;
  border-left: 3px solid #2563eb;
  background: #eff6ff;
  color: #475569;
}

.file-preview-markdown-body :deep(ul),
.file-preview-markdown-body :deep(ol) {
  margin: 10px 0 14px 22px;
  padding: 0;
}

.file-preview-markdown-body :deep(li) {
  margin: 4px 0;
}

.file-preview-markdown-body :deep(img) {
  display: block;
  max-width: 100%;
  margin: 14px auto;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.file-preview-markdown-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 16px 0;
  font-size: 0.88rem;
}

.file-preview-markdown-body :deep(th),
.file-preview-markdown-body :deep(td) {
  border: 1px solid #e2e8f0;
  padding: 9px 11px;
  text-align: left;
  vertical-align: top;
}

.file-preview-markdown-body :deep(th) {
  background: #eef2ff;
  color: #3730a3;
  font-weight: 700;
}

.file-preview-markdown-body :deep(a) {
  color: #2563eb;
  text-decoration: none;
}

.file-preview-markdown-body :deep(a:hover) {
  text-decoration: underline;
}

.file-preview-state {
  margin: 24px;
  padding: 14px 16px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #ffffff;
  color: #475569;
  font-size: 0.86rem;
}

.file-preview-state.is-error {
  border-color: #fecaca;
  background: #fef2f2;
  color: #b91c1c;
}

.preview-fade-enter-active,
.preview-fade-leave-active {
  transition: opacity 0.18s ease;
}

.preview-fade-enter-from,
.preview-fade-leave-to {
  opacity: 0;
}

/* ── Transitions ── */
.trace-expand-enter-active,
.trace-expand-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}

.trace-expand-enter-from,
.trace-expand-leave-to {
  opacity: 0;
  max-height: 0;
}

.trace-expand-enter-to,
.trace-expand-leave-from {
  opacity: 1;
  max-height: 700px;
}

/* Command-block enter animation */
.cmd-enter-enter-active {
  transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.cmd-enter-enter-from {
  opacity: 0;
  transform: translateY(-16px) scale(0.96);
}

.cmd-enter-leave-active {
  transition: all 0.2s ease-in;
}

.cmd-enter-leave-to {
  opacity: 0;
  transform: translateX(20px);
}

/* Tool output reveal */
.tool-reveal-enter-active,
.tool-reveal-leave-active {
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}

.tool-reveal-enter-from,
.tool-reveal-leave-to {
  opacity: 0;
  max-height: 0;
  margin-top: 0;
}

.tool-reveal-enter-to,
.tool-reveal-leave-from {
  opacity: 1;
  max-height: 350px;
}

/* ── Input Section ── */
.input-section {
  flex-shrink: 0;
  padding-top: 12px;
  padding-bottom: 4px;
}

.input-bar {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  padding: 16px 18px;
  background: var(--bg-surface);
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
  transition: border-color 0.2s, box-shadow 0.2s;
}

.input-bar:focus-within {
  border-color: var(--brand);
  box-shadow: 0 4px 20px rgba(124, 58, 237, 0.12);
}

.input-field {
  flex: 1;
  border: none;
  background: transparent;
  resize: none;
  font-size: 1.1rem;
  line-height: 1.6;
  color: var(--text-strong);
  outline: none;
  min-height: 52px;
  max-height: 140px;
  padding: 4px 0;
  overflow-y: auto;
  scrollbar-width: none;
}

.input-field::-webkit-scrollbar { display: none; }

.input-field::placeholder {
  color: var(--text-muted);
}

.input-field:disabled {
  opacity: 0.5;
}

.send-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: white;
  background-color: var(--brand);
  transition: background-color 0.2s, transform 0.1s;
  box-shadow: 0 2px 8px rgba(124, 58, 237, 0.4);
}

.send-btn:hover:not(:disabled) {
  background-color: var(--brand-strong);
  transform: scale(1.05);
}

.send-btn:disabled {
  background-color: var(--line-strong);
  cursor: not-allowed;
  box-shadow: none;
}

/* ── Responsive ── */
@media (max-width: 768px) {
  .message-row {
    max-width: 95%;
  }

  .message-row.is-user {
    max-width: 85%;
  }

  .bubble-text {
    font-size: 0.92rem;
  }

  .trace-body {
    max-height: 300px;
  }

  .trace-artifact-panel .artifact-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .file-preview-overlay {
    padding: 12px;
  }

  .file-preview-dialog {
    width: 100vw;
    height: 92vh;
  }
}

@media (max-width: 480px) {
  .avatar-col {
    width: 30px;
    height: 30px;
  }


  .bubble {
    padding: 10px 14px;
  }

  .input-bar {
    padding: 12px 14px;
  }

  .send-btn {
    width: 32px;
    height: 32px;
  }

  .input-field {
    min-height: 44px;
    font-size: 1rem;
  }

  .trace-artifact-panel .artifact-grid {
    grid-template-columns: 1fr;
  }

  .file-preview-head {
    align-items: flex-start;
  }
}
</style>
