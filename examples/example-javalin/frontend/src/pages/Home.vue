<script setup lang="ts">
import { Link } from '@inertiajs/vue3'

defineProps<{
  auth: { user: { name: string } }
}>()
</script>

<template>
  <div style="max-width: 600px; margin: 40px auto; font-family: sans-serif;">
    <h1>Inertia.js + Java</h1>
    <p>Welcome, {{ auth.user.name }}!</p>
    <p>This is a demo application showing Inertia.js working with a Spring Boot backend.</p>
    <nav>
      <Link href="/events" style="color: #2563eb;">View Events →</Link>
    </nav>
  </div>
</template>
